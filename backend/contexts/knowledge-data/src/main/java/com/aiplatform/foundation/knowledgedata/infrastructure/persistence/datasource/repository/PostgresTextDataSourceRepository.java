package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.repository;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNameConflictException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceQuotaExceededException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextContentAttachment;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceCreation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceMutationOutcome;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourcePage;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourcePublication;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.TextDataSourceSummary;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextDataSource;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.TextContentEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.TextDataSourceEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.TextDataSourceRequestEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextContentMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper.TextDataSourceRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.entity.KnowledgeSpaceEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper.KnowledgeSpaceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
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
    private final TextContentMapper contentMapper;
    private final TextDataSourceRequestMapper requestMapper;
    private final KnowledgeSpaceMapper knowledgeSpaceMapper;
    private final TransactionTemplate transactions;
    private final EntityIdGenerator identifiers;

    public PostgresTextDataSourceRepository(
            TextDataSourceMapper mapper,
            TextContentMapper contentMapper,
            TextDataSourceRequestMapper requestMapper,
            KnowledgeSpaceMapper knowledgeSpaceMapper,
            PlatformTransactionManager transactionManager,
            EntityIdGenerator identifiers) {
        this.mapper = Objects.requireNonNull(mapper, "文本数据源映射器不能为空");
        this.contentMapper = Objects.requireNonNull(
                contentMapper,
                "文本内容映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "文本数据源幂等请求映射器不能为空");
        this.knowledgeSpaceMapper = Objects.requireNonNull(
                knowledgeSpaceMapper,
                "知识空间映射器不能为空");
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
        var row = mapper.selectOne(
                sourceQuery(
                        tenantId.value(),
                        knowledgeSpaceId.value(),
                        dataSourceId.value()));
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
    public TextDataSourcePage findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        var page = mapper.selectPage(
                new Page<TextDataSourceEntity>(pageNumber, pageSize, true),
                Wrappers.<TextDataSourceEntity>lambdaQuery()
                        .eq(TextDataSourceEntity::getTenantId, tenantId.value())
                        .eq(
                                TextDataSourceEntity::getKnowledgeSpaceId,
                                knowledgeSpaceId.value())
                        .orderByDesc(
                                TextDataSourceEntity::getCreatedAt,
                                TextDataSourceEntity::getId));
        return new TextDataSourcePage(
                page.getRecords().stream()
                        .map(PostgresTextDataSourceRepository::toSummary)
                        .toList(),
                pageNumber,
                pageSize,
                page.getTotal());
    }

    private TextDataSourceMutationOutcome createInTransaction(
            TextDataSourceCreation creation,
            int tenantLimit) {
        var source = creation.source();
        var tenantId = source.tenantId().value();
        var keyHash = sha256(creation.idempotencyKey());
        mapper.lockTenant(Long.toString(tenantId));

        var existing = findRequest(tenantId, CREATE, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, creation.requestFingerprint());
        }
        if (knowledgeSpaceMapper.selectCount(
                Wrappers.<KnowledgeSpaceEntity>lambdaQuery()
                        .eq(KnowledgeSpaceEntity::getTenantId, tenantId)
                        .eq(
                                KnowledgeSpaceEntity::getId,
                                source.knowledgeSpaceId().value())
                        .ne(KnowledgeSpaceEntity::getStatus, "DELETED")) != 1L) {
            throw new DataSourceNotFoundException();
        }
        if (mapper.selectCount(
                Wrappers.<TextDataSourceEntity>lambdaQuery()
                        .eq(TextDataSourceEntity::getTenantId, tenantId)) >= tenantLimit) {
            throw new DataSourceQuotaExceededException();
        }

        try {
            mapper.insert(toEntity(source));
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
        var existing = findRequest(tenantId, ATTACH_CONTENT, keyHash);
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

        existing = findRequest(tenantId, ATTACH_CONTENT, keyHash);
        if (existing != null) {
            return replay(tenantId, existing, attachment.requestFingerprint());
        }
        if (currentRow.getVersion() != attachment.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }

        var current = toCurrentDomain(currentRow);
        var changed = current.attach(attachment.content());
        var content = changed.currentContent();
        contentMapper.insert(toContentEntity(changed, content));
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
        var existing = findRequest(tenantId, PUBLISH, keyHash);
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

        existing = findRequest(tenantId, PUBLISH, keyHash);
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
            TextDataSourceRequestEntity request,
            String fingerprint) {
        if (!request.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException();
        }
        var knowledgeSpaceId = request.getKnowledgeSpaceId();
        var dataSourceId = request.getDataSourceId();
        var identity = mapper.selectOne(
                sourceQuery(tenantId, knowledgeSpaceId, dataSourceId));
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
                new TenantId(identity.getTenantId()),
                new KnowledgeSpaceId(knowledgeSpaceId),
                new DataSourceId(dataSourceId),
                identity.getName(),
                DataSourceStatus.valueOf(request.getResultStatus()),
                request.getResultVersion(),
                request.getResultContentVersion(),
                request.getResultPublishedContentVersion(),
                content,
                new CreatorId(identity.getCreatedBy()),
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
        var existing = findRequest(
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

    private TextDataSource toCurrentDomain(TextDataSourceEntity row) {
        var tenantId = row.getTenantId();
        var knowledgeSpaceId = row.getKnowledgeSpaceId();
        var dataSourceId = row.getId();
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
                new CreatorId(row.getCreatedBy()),
                row.getCreatedAt());
    }

    private static TextDataSourceSummary toSummary(TextDataSourceEntity row) {
        return new TextDataSourceSummary(
                new DataSourceId(row.getId()),
                new KnowledgeSpaceId(row.getKnowledgeSpaceId()),
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
        var row = contentMapper.selectOne(
                Wrappers.<TextContentEntity>lambdaQuery()
                        .eq(TextContentEntity::getTenantId, tenantId)
                        .eq(TextContentEntity::getKnowledgeSpaceId, knowledgeSpaceId)
                        .eq(TextContentEntity::getDataSourceId, dataSourceId)
                        .eq(TextContentEntity::getVersion, version));
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
        var changed = mapper.update(
                null,
                Wrappers.<TextDataSourceEntity>lambdaUpdate()
                        .eq(TextDataSourceEntity::getTenantId, source.tenantId().value())
                        .eq(
                                TextDataSourceEntity::getKnowledgeSpaceId,
                                source.knowledgeSpaceId().value())
                        .eq(TextDataSourceEntity::getId, source.id().value())
                        .eq(TextDataSourceEntity::getVersion, expectedVersion)
                        .set(TextDataSourceEntity::getStatus, source.status().name())
                        .set(TextDataSourceEntity::getVersion, source.version())
                        .set(
                                TextDataSourceEntity::getContentVersion,
                                source.contentVersion())
                        .set(
                                TextDataSourceEntity::getPublishedContentVersion,
                                source.publishedContentVersion()));
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
        var entity = new TextDataSourceRequestEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(source.tenantId().value());
        entity.setOperation(operation);
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(fingerprint);
        entity.setKnowledgeSpaceId(source.knowledgeSpaceId().value());
        entity.setDataSourceId(source.id().value());
        entity.setResultStatus(source.status().name());
        entity.setResultVersion(source.version());
        entity.setResultContentVersion(source.contentVersion());
        entity.setResultPublishedContentVersion(source.publishedContentVersion());
        entity.setCreatedAt(createdAt);
        requestMapper.insert(entity);
    }

    private TextDataSourceRequestEntity findRequest(
            long tenantId,
            String operation,
            String keyHash) {
        return requestMapper.selectOne(
                Wrappers.<TextDataSourceRequestEntity>lambdaQuery()
                        .eq(TextDataSourceRequestEntity::getTenantId, tenantId)
                        .eq(TextDataSourceRequestEntity::getOperation, operation)
                        .eq(TextDataSourceRequestEntity::getIdempotencyKeyHash, keyHash));
    }

    private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
            TextDataSourceEntity> sourceQuery(
                    long tenantId,
                    long knowledgeSpaceId,
                    long dataSourceId) {
        return Wrappers.<TextDataSourceEntity>lambdaQuery()
                .eq(TextDataSourceEntity::getTenantId, tenantId)
                .eq(TextDataSourceEntity::getKnowledgeSpaceId, knowledgeSpaceId)
                .eq(TextDataSourceEntity::getId, dataSourceId);
    }

    private static TextDataSourceEntity toEntity(TextDataSource source) {
        var entity = new TextDataSourceEntity();
        entity.setId(source.id().value());
        entity.setTenantId(source.tenantId().value());
        entity.setKnowledgeSpaceId(source.knowledgeSpaceId().value());
        entity.setName(source.name());
        entity.setStatus(source.status().name());
        entity.setVersion(source.version());
        entity.setContentVersion(source.contentVersion());
        entity.setPublishedContentVersion(source.publishedContentVersion());
        entity.setCreatedBy(source.createdBy().value());
        entity.setCreatedAt(source.createdAt());
        return entity;
    }

    private TextContentEntity toContentEntity(
            TextDataSource source,
            TextContentReference content) {
        var entity = new TextContentEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(source.tenantId().value());
        entity.setKnowledgeSpaceId(source.knowledgeSpaceId().value());
        entity.setDataSourceId(source.id().value());
        entity.setVersion(content.version());
        entity.setObjectKey(content.objectKey());
        entity.setSha256(content.sha256());
        entity.setByteSize(content.byteSize());
        entity.setMediaType(content.mediaType());
        entity.setCreatedAt(content.createdAt());
        return entity;
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

}
