package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.datasource;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.DataSourceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextContentAttachment;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextDataSourceCreation;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextDataSourceMutationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextDataSourcePublication;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextDataSourceSlice;
import com.aiplatform.foundation.knowledgedata.application.port.out.datasource.TextDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.model.TextDataSource;
import com.aiplatform.foundation.security.identity.MembershipId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务实现文本数据源状态变更和历史幂等重放。
 */
public final class PostgresTextDataSourceRepository implements TextDataSourceRepository {

    private static final int MAX_CURSOR_LENGTH = 512;
    private static final String CREATE = "CREATE";
    private static final String ATTACH_CONTENT = "ATTACH_CONTENT";
    private static final String PUBLISH = "PUBLISH";

    private final TextDataSourceMapper mapper;
    private final TransactionTemplate transactions;
    private final EntityIdGenerator identifiers;

    public PostgresTextDataSourceRepository(
            TextDataSourceMapper mapper,
            PlatformTransactionManager transactionManager,
            EntityIdGenerator identifiers) {
        this.mapper = Objects.requireNonNull(mapper, "文本数据源映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
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

    @Override
    public TextDataSource findById(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        var row = mapper.findById(
                tenantId.value(),
                knowledgeSpaceId.value(),
                dataSourceId.value());
        if (row == null) {
            throw new DataSourceNotFoundException();
        }
        return toCurrentDomain(row);
    }

    @Override
    public Optional<TextDataSourceMutationOutcome> findAttachmentReplay(
            TenantId tenantId,
            String idempotencyKey,
            String requestFingerprint) {
        return findReplay(
                tenantId,
                ATTACH_CONTENT,
                idempotencyKey,
                requestFingerprint);
    }

    @Override
    public Optional<TextDataSourceMutationOutcome> findPublicationReplay(
            TenantId tenantId,
            String idempotencyKey,
            String requestFingerprint) {
        return findReplay(
                tenantId,
                PUBLISH,
                idempotencyKey,
                requestFingerprint);
    }

    @Override
    public TextDataSourceSlice findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            String cursor,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<TextDataSourceRow> rows;
        if (cursor == null) {
            rows = mapper.findFirstPage(
                    tenantId.value(),
                    knowledgeSpaceId.value(),
                    fetchLimit);
        } else {
            var position = decodeCursor(cursor);
            rows = mapper.findAfter(
                    tenantId.value(),
                    knowledgeSpaceId.value(),
                    position.createdAt(),
                    position.id(),
                    fetchLimit);
        }
        var hasNext = rows.size() > limit;
        var pageRows = hasNext ? rows.subList(0, limit) : rows;
        var items = pageRows.stream()
                .map(PostgresTextDataSourceRepository::toSummary)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getCreatedAt(),
                        Long.parseLong(pageRows.getLast().getId()))
                : null;
        return new TextDataSourceSlice(items, nextCursor);
    }

    private TextDataSourceMutationOutcome createInTransaction(
            TextDataSourceCreation creation,
            int tenantLimit) {
        var source = creation.source();
        var tenantId = source.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

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
            long tenantId,
            TextDataSourceRequestRow request,
            String fingerprint) {
        if (!request.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException();
        }
        var knowledgeSpaceId = Long.parseLong(request.getKnowledgeSpaceId());
        var dataSourceId = Long.parseLong(request.getDataSourceId());
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
                MembershipId.parse(identity.getCreatedBy()),
                identity.getCreatedAt());
        return new TextDataSourceMutationOutcome(source, true);
    }

    private Optional<TextDataSourceMutationOutcome> findReplay(
            TenantId tenantId,
            String operation,
            String idempotencyKey,
            String requestFingerprint) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        var existing = mapper.findRequest(
                tenantId.value(),
                operation,
                sha256(idempotencyKey));
        return existing == null
                ? Optional.empty()
                : Optional.of(replay(
                        tenantId.value(),
                        existing,
                        requestFingerprint));
    }

    private TextDataSource toCurrentDomain(TextDataSourceRow row) {
        var tenantId = Long.parseLong(row.getTenantId());
        var knowledgeSpaceId = Long.parseLong(row.getKnowledgeSpaceId());
        var dataSourceId = Long.parseLong(row.getId());
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
                MembershipId.parse(row.getCreatedBy()),
                row.getCreatedAt());
    }

    private static TextDataSourceSummary toSummary(TextDataSourceRow row) {
        return new TextDataSourceSummary(
                DataSourceId.parse(row.getId()),
                KnowledgeSpaceId.parse(row.getKnowledgeSpaceId()),
                row.getName(),
                DataSourceStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getContentVersion(),
                row.getPublishedContentVersion(),
                row.getCreatedAt());
    }

    private TextContentReference requireContent(
            long tenantId,
            long knowledgeSpaceId,
            long dataSourceId,
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
                identifiers.nextId(),
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

    private static String encodeCursor(Instant createdAt, long id) {
        var payload = createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPosition decodeCursor(String cursor) {
        if (cursor == null
                || cursor.isBlank()
                || cursor.length() > MAX_CURSOR_LENGTH
                || !cursor.equals(cursor.trim())) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        try {
            var payload = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            var separator = payload.lastIndexOf('|');
            if (separator < 1 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("游标格式不正确");
            }
            return new CursorPosition(
                    Instant.parse(payload.substring(0, separator)),
                    Long.parseLong(payload.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("游标格式不正确", exception);
        }
    }

    private record CursorPosition(Instant createdAt, long id) {
    }
}
