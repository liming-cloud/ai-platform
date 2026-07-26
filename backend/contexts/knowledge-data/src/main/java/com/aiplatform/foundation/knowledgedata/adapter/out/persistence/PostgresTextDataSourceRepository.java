package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextContentAttachment;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceMutationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourcePublication;
import com.aiplatform.foundation.knowledgedata.application.port.out.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.model.TextDataSource;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务实现文本数据源状态变更和历史幂等重放。
 */
public final class PostgresTextDataSourceRepository implements TextDataSourceRepository {

    private static final String CREATE = "CREATE";
    private static final String ATTACH_CONTENT = "ATTACH_CONTENT";
    private static final String PUBLISH = "PUBLISH";

    private final TextDataSourceMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresTextDataSourceRepository(
            TextDataSourceMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "文本数据源映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public TextDataSourceMutationOutcome create(
            TextDataSourceCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "数据源创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户数据源配额必须大于零");
        }
        var result = transactions.execute(status -> createInTransaction(
                creation,
                tenantLimit));
        return Objects.requireNonNull(result, "数据源创建事务没有返回结果");
    }

    @Override
    public TextDataSourceMutationOutcome attach(TextContentAttachment attachment) {
        Objects.requireNonNull(attachment, "内容关联请求不能为空");
        var result = transactions.execute(status -> attachInTransaction(attachment));
        return Objects.requireNonNull(result, "内容关联事务没有返回结果");
    }

    @Override
    public TextDataSourceMutationOutcome publish(TextDataSourcePublication publication) {
        Objects.requireNonNull(publication, "数据源发布请求不能为空");
        var result = transactions.execute(status -> publishInTransaction(publication));
        return Objects.requireNonNull(result, "数据源发布事务没有返回结果");
    }

    private TextDataSourceMutationOutcome createInTransaction(
            TextDataSourceCreation creation,
            int tenantLimit) {
        var source = creation.source();
        var tenantId = source.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(tenantId.toString());

        var existing = mapper.findRequest(tenantId, CREATE, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, creation.requestFingerprint());
        }
        if (mapper.countKnowledgeSpace(
                tenantId,
                source.knowledgeSpaceId().value()) != 1) {
            throw new DataSourceNotFoundException();
        }
        if (mapper.countTenantSources(tenantId) >= tenantLimit) {
            throw new DataSourceQuotaExceededException();
        }

        try {
            mapper.insertSource(
                    tenantId,
                    source.knowledgeSpaceId().value(),
                    source.id().value(),
                    source.name(),
                    source.status().name(),
                    source.version(),
                    source.contentVersion(),
                    source.publishedContentVersion(),
                    source.createdBy().value(),
                    source.createdAt());
        } catch (DuplicateKeyException exception) {
            throw new DataSourceNameConflictException();
        }
        saveRequest(
                CREATE,
                keyHash,
                creation.requestFingerprint(),
                source,
                source.createdAt());
        saveEvent("TEXT_DATA_SOURCE_CREATED", source, source.createdAt());
        return new TextDataSourceMutationOutcome(source, false);
    }

    private TextDataSourceMutationOutcome attachInTransaction(
            TextContentAttachment attachment) {
        var tenantId = attachment.tenantId().value();
        var keyHash = sha256(attachment.idempotencyKey());
        var existing = mapper.findRequest(tenantId, ATTACH_CONTENT, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, attachment.requestFingerprint());
        }

        var currentRow = mapper.findByIdForUpdate(
                tenantId,
                attachment.knowledgeSpaceId().value(),
                attachment.dataSourceId().value());
        if (currentRow == null) {
            throw new DataSourceNotFoundException();
        }

        existing = mapper.findRequest(tenantId, ATTACH_CONTENT, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, attachment.requestFingerprint());
        }
        if (currentRow.getVersion() != attachment.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }

        var current = toCurrentDomain(currentRow);
        var changed = current.attach(attachment.content());
        var content = changed.currentContent();
        mapper.insertContent(
                tenantId,
                changed.knowledgeSpaceId().value(),
                changed.id().value(),
                content.version(),
                content.objectKey(),
                content.sha256(),
                content.byteSize(),
                content.mediaType(),
                content.createdAt());
        updateSource(changed, attachment.expectedVersion());
        saveRequest(
                ATTACH_CONTENT,
                keyHash,
                attachment.requestFingerprint(),
                changed,
                content.createdAt());
        saveEvent(
                "TEXT_DATA_SOURCE_CONTENT_ATTACHED",
                changed,
                content.createdAt());
        return new TextDataSourceMutationOutcome(changed, false);
    }

    private TextDataSourceMutationOutcome publishInTransaction(
            TextDataSourcePublication publication) {
        var tenantId = publication.tenantId().value();
        var keyHash = sha256(publication.idempotencyKey());
        var existing = mapper.findRequest(tenantId, PUBLISH, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, publication.requestFingerprint());
        }

        var currentRow = mapper.findByIdForUpdate(
                tenantId,
                publication.knowledgeSpaceId().value(),
                publication.dataSourceId().value());
        if (currentRow == null) {
            throw new DataSourceNotFoundException();
        }

        existing = mapper.findRequest(tenantId, PUBLISH, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, publication.requestFingerprint());
        }
        if (currentRow.getVersion() != publication.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }

        var changed = toCurrentDomain(currentRow).publish();
        updateSource(changed, publication.expectedVersion());
        saveRequest(
                PUBLISH,
                keyHash,
                publication.requestFingerprint(),
                changed,
                publication.publishedAt());
        saveEvent(
                "TEXT_DATA_SOURCE_PUBLISHED",
                changed,
                publication.publishedAt());
        return new TextDataSourceMutationOutcome(changed, false);
    }

    private TextDataSourceMutationOutcome replay(
            UUID tenantId,
            TextDataSourceRequestRow request,
            String fingerprint) {
        if (!request.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException();
        }
        var knowledgeSpaceId = UUID.fromString(request.getKnowledgeSpaceId());
        var dataSourceId = UUID.fromString(request.getDataSourceId());
        var identity = mapper.findById(tenantId, knowledgeSpaceId, dataSourceId);
        if (identity == null) {
            throw new IllegalStateException("幂等记录引用的数据源不存在");
        }
        var content = request.getResultContentVersion() == 0
                ? null
                : requireContent(
                        tenantId,
                        knowledgeSpaceId,
                        dataSourceId,
                        request.getResultContentVersion());
        var source = TextDataSource.reconstitute(
                TenantId.parse(identity.getTenantId()),
                new KnowledgeSpaceId(knowledgeSpaceId),
                new DataSourceId(dataSourceId),
                identity.getName(),
                DataSourceStatus.valueOf(request.getResultStatus()),
                request.getResultVersion(),
                request.getResultContentVersion(),
                request.getResultPublishedContentVersion(),
                content,
                new MembershipId(UUID.fromString(identity.getCreatedBy())),
                identity.getCreatedAt());
        return new TextDataSourceMutationOutcome(source, true);
    }

    private TextDataSource toCurrentDomain(TextDataSourceRow row) {
        var tenantId = UUID.fromString(row.getTenantId());
        var knowledgeSpaceId = UUID.fromString(row.getKnowledgeSpaceId());
        var dataSourceId = UUID.fromString(row.getId());
        var content = row.getContentVersion() == 0
                ? null
                : requireContent(
                        tenantId,
                        knowledgeSpaceId,
                        dataSourceId,
                        row.getContentVersion());
        return TextDataSource.reconstitute(
                new TenantId(tenantId),
                new KnowledgeSpaceId(knowledgeSpaceId),
                new DataSourceId(dataSourceId),
                row.getName(),
                DataSourceStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getContentVersion(),
                row.getPublishedContentVersion(),
                content,
                new MembershipId(UUID.fromString(row.getCreatedBy())),
                row.getCreatedAt());
    }

    private TextContentReference requireContent(
            UUID tenantId,
            UUID knowledgeSpaceId,
            UUID dataSourceId,
            int version) {
        var row = mapper.findContent(
                tenantId,
                knowledgeSpaceId,
                dataSourceId,
                version);
        if (row == null) {
            throw new IllegalStateException("数据源引用的内容版本不存在");
        }
        return new TextContentReference(
                row.getVersion(),
                row.getObjectKey(),
                row.getSha256(),
                row.getByteSize(),
                row.getMediaType(),
                row.getCreatedAt());
    }

    private void updateSource(TextDataSource source, long expectedVersion) {
        var changed = mapper.updateSource(
                source.tenantId().value(),
                source.knowledgeSpaceId().value(),
                source.id().value(),
                source.status().name(),
                source.version(),
                source.contentVersion(),
                source.publishedContentVersion(),
                expectedVersion);
        if (changed != 1) {
            throw new DataSourceConcurrentModificationException();
        }
    }

    private void saveRequest(
            String operation,
            String keyHash,
            String fingerprint,
            TextDataSource source,
            Instant createdAt) {
        mapper.insertRequest(
                source.tenantId().value(),
                operation,
                keyHash,
                fingerprint,
                source.knowledgeSpaceId().value(),
                source.id().value(),
                source.status().name(),
                source.version(),
                source.contentVersion(),
                source.publishedContentVersion(),
                createdAt);
    }

    private void saveEvent(
            String eventType,
            TextDataSource source,
            Instant occurredAt) {
        var payload = """
                {"knowledgeSpaceId":"%s","dataSourceId":"%s","version":%d}
                """.formatted(
                        source.knowledgeSpaceId().value(),
                        source.id().value(),
                        source.version()).strip();
        mapper.insertOutboxEvent(
                source.tenantId().value(),
                UUID.randomUUID(),
                source.id().value(),
                eventType,
                payload,
                occurredAt);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
