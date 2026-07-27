package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.repository;

import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantIdempotencyConflictException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantNameConflictException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantVersionQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantPage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreation;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionCreationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublication;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantVersionPublicationOutcome;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantPage;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantSummary;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.Assistant;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersion;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.AssistantVersionStatus;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CreatorId;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantRequestEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity.AssistantVersionEntity;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantRequestMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.mapper.AssistantVersionMapper;
import com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.projection.PublishedAssistantRow;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务锁实现助手配额、连续版本和幂等语义。
 */
public final class PostgresAssistantRepository implements AssistantRepository {

    private static final String CREATE_ASSISTANT = "CREATE_ASSISTANT";
    private static final String CREATE_VERSION = "CREATE_VERSION";
    private static final String PUBLISH_VERSION = "PUBLISH_VERSION";

    private final AssistantMapper mapper;
    private final AssistantRequestMapper requestMapper;
    private final AssistantVersionMapper versionMapper;
    private final EntityIdGenerator identifiers;
    private final TransactionTemplate transactions;

    public PostgresAssistantRepository(
            AssistantMapper mapper,
            AssistantRequestMapper requestMapper,
            AssistantVersionMapper versionMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "助手映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "助手幂等请求映射器不能为空");
        this.versionMapper = Objects.requireNonNull(
                versionMapper,
                "助手版本映射器不能为空");
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "标识生成器不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public AssistantCreationOutcome create(
            AssistantCreation creation,
            int tenantLimit) {
        Objects.requireNonNull(creation, "助手创建请求不能为空");
        if (tenantLimit < 1) {
            throw new IllegalArgumentException("租户助手配额必须大于零");
        }
        var result = transactions.execute(
                status -> createAssistantInTransaction(creation, tenantLimit));
        return Objects.requireNonNull(result, "助手创建事务没有返回结果");
    }

    @Override
    public Optional<Assistant> findAssistant(
            TenantId tenantId,
            AssistantId assistantId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantId, "助手标识不能为空");
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<AssistantEntity>()
                        .eq(AssistantEntity::getTenantId, tenantId.value())
                        .eq(AssistantEntity::getId, assistantId.value())))
                .map(PostgresAssistantRepository::toAssistant);
    }

    @Override
    public AssistantPage findPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        var page = mapper.selectPage(
                new Page<AssistantEntity>(pageNumber, pageSize),
                new LambdaQueryWrapper<AssistantEntity>()
                        .eq(AssistantEntity::getTenantId, tenantId.value())
                        .orderByDesc(AssistantEntity::getCreatedAt)
                        .orderByDesc(AssistantEntity::getId));
        var items = page.getRecords().stream()
                .map(PostgresAssistantRepository::toAssistant)
                .toList();
        return new AssistantPage(items, page.getTotal());
    }

    @Override
    public PublishedAssistantPage findPublishedPage(
            TenantId tenantId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (pageNumber < 1L || pageSize < 1L || pageSize > 100L) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        var page = mapper.findPublishedPage(
                new Page<PublishedAssistantRow>(pageNumber, pageSize),
                tenantId.value());
        var items = page.getRecords().stream()
                .map(PostgresAssistantRepository::toPublishedSummary)
                .toList();
        return new PublishedAssistantPage(items, page.getTotal());
    }

    @Override
    public AssistantVersionCreationOutcome createVersion(
            AssistantVersionCreation creation,
            int perAssistantLimit) {
        Objects.requireNonNull(creation, "助手版本创建请求不能为空");
        if (perAssistantLimit < 1) {
            throw new IllegalArgumentException("助手版本配额必须大于零");
        }
        var result = transactions.execute(
                status -> createVersionInTransaction(creation, perAssistantLimit));
        return Objects.requireNonNull(result, "助手版本创建事务没有返回结果");
    }

    @Override
    public Optional<AssistantVersion> findVersion(
            TenantId tenantId,
            AssistantVersionId assistantVersionId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        return Optional.ofNullable(versionMapper.selectOne(
                        versionQuery(tenantId.value(), assistantVersionId.value())))
                .map(PostgresAssistantRepository::toVersion);
    }

    @Override
    public Optional<AssistantVersionPublicationOutcome> replayPublication(
            TenantId tenantId,
            AssistantVersionId assistantVersionId,
            String idempotencyKey,
            String requestFingerprint) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        var request = findRequest(
                tenantId.value(),
                PUBLISH_VERSION,
                sha256(idempotencyKey));
        if (request == null) {
            return Optional.empty();
        }
        requireFingerprint(request, requestFingerprint);
        if (assistantVersionId.value() != request.getAssistantVersionId()) {
            throw new AssistantIdempotencyConflictException();
        }
        var row = versionMapper.selectOne(
                versionQuery(tenantId.value(), assistantVersionId.value()));
        if (row == null) {
            throw new IllegalStateException("幂等记录引用的助手版本不存在");
        }
        var version = toVersion(row);
        if (version.status() != AssistantVersionStatus.PUBLISHED) {
            throw new IllegalStateException("发布幂等记录引用的版本未发布");
        }
        return Optional.of(new AssistantVersionPublicationOutcome(version, true));
    }

    @Override
    public AssistantVersionPublicationOutcome publishVersion(
            AssistantVersionPublication publication) {
        Objects.requireNonNull(publication, "助手版本发布请求不能为空");
        var result = transactions.execute(
                status -> publishVersionInTransaction(publication));
        return Objects.requireNonNull(result, "助手版本发布事务没有返回结果");
    }

    private AssistantCreationOutcome createAssistantInTransaction(
            AssistantCreation creation,
            int tenantLimit) {
        var assistant = creation.assistant();
        var tenantId = assistant.tenantId().value();
        mapper.lockScope(Long.toString(tenantId));
        var keyHash = sha256(creation.idempotencyKey());
        var existing = findRequest(tenantId, CREATE_ASSISTANT, keyHash);
        if (existing != null) {
            requireFingerprint(existing, creation.requestFingerprint());
            var replayed = mapper.selectOne(new LambdaQueryWrapper<AssistantEntity>()
                    .eq(AssistantEntity::getTenantId, tenantId)
                    .eq(AssistantEntity::getId, existing.getAssistantId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的助手不存在");
            }
            return new AssistantCreationOutcome(toAssistant(replayed), true);
        }
        if (mapper.selectCount(new LambdaQueryWrapper<AssistantEntity>()
                .eq(AssistantEntity::getTenantId, tenantId)) >= tenantLimit) {
            throw new AssistantQuotaExceededException();
        }
        try {
            mapper.insert(toEntity(assistant));
            requestMapper.insert(requestEntity(
                    tenantId,
                    CREATE_ASSISTANT,
                    keyHash,
                    creation.requestFingerprint(),
                    assistant.id().value(),
                    null,
                    assistant.createdAt()));
        } catch (DuplicateKeyException exception) {
            throw new AssistantNameConflictException();
        }
        return new AssistantCreationOutcome(assistant, false);
    }

    private AssistantVersionCreationOutcome createVersionInTransaction(
            AssistantVersionCreation creation,
            int perAssistantLimit) {
        var expected = creation.expectedAssistant();
        var tenantId = expected.tenantId().value();
        var assistantId = expected.id().value();
        mapper.lockScope(tenantId + ":" + assistantId);
        var keyHash = sha256(creation.idempotencyKey());
        var existing = findRequest(tenantId, CREATE_VERSION, keyHash);
        if (existing != null) {
            requireFingerprint(existing, creation.requestFingerprint());
            var replayed = versionMapper.selectOne(versionQuery(
                    tenantId,
                    existing.getAssistantVersionId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的助手版本不存在");
            }
            return new AssistantVersionCreationOutcome(toVersion(replayed), true);
        }
        var locked = mapper.findAssistantForUpdate(tenantId, assistantId);
        if (locked == null) {
            throw new AssistantNotFoundException();
        }
        var current = toAssistant(locked);
        if (current.version() != expected.version()
                || current.latestVersionNumber() != expected.latestVersionNumber()) {
            throw new AssistantConcurrentModificationException();
        }
        if (versionMapper.selectCount(new LambdaQueryWrapper<AssistantVersionEntity>()
                .eq(AssistantVersionEntity::getTenantId, tenantId)
                .eq(AssistantVersionEntity::getAssistantId, assistantId))
                >= perAssistantLimit) {
            throw new AssistantVersionQuotaExceededException();
        }
        var advanced = creation.advancedAssistant();
        var version = creation.assistantVersion();
        try {
            if (mapper.update(
                    null,
                    new LambdaUpdateWrapper<AssistantEntity>()
                            .eq(AssistantEntity::getTenantId, tenantId)
                            .eq(AssistantEntity::getId, assistantId)
                            .eq(AssistantEntity::getVersion, expected.version())
                            .set(
                                    AssistantEntity::getLatestVersionNumber,
                                    advanced.latestVersionNumber())
                            .set(AssistantEntity::getVersion, advanced.version()))
                    != 1) {
                throw new AssistantConcurrentModificationException();
            }
            versionMapper.insert(toVersionEntity(version));
            requestMapper.insert(requestEntity(
                    tenantId,
                    CREATE_VERSION,
                    keyHash,
                    creation.requestFingerprint(),
                    assistantId,
                    version.id().value(),
                    version.createdAt()));
        } catch (DuplicateKeyException exception) {
            throw new AssistantConcurrentModificationException();
        }
        return new AssistantVersionCreationOutcome(version, false);
    }

    private AssistantVersionPublicationOutcome publishVersionInTransaction(
            AssistantVersionPublication publication) {
        var version = publication.publishedVersion();
        var tenantId = version.tenantId().value();
        mapper.lockScope(tenantId + ":" + version.id().value());
        var keyHash = sha256(publication.idempotencyKey());
        var existing = findRequest(tenantId, PUBLISH_VERSION, keyHash);
        if (existing != null) {
            requireFingerprint(existing, publication.requestFingerprint());
            var replayed = versionMapper.selectOne(versionQuery(
                    tenantId,
                    existing.getAssistantVersionId()));
            if (replayed == null) {
                throw new IllegalStateException("幂等记录引用的助手版本不存在");
            }
            return new AssistantVersionPublicationOutcome(
                    toVersion(replayed),
                    true);
        }
        if (versionMapper.update(
                null,
                new LambdaUpdateWrapper<AssistantVersionEntity>()
                        .eq(AssistantVersionEntity::getTenantId, tenantId)
                        .eq(AssistantVersionEntity::getId, version.id().value())
                        .eq(AssistantVersionEntity::getStatus, AssistantVersionStatus.DRAFT.name())
                        .eq(AssistantVersionEntity::getVersion, publication.expectedVersion())
                        .set(AssistantVersionEntity::getStatus, version.status().name())
                        .set(AssistantVersionEntity::getVersion, version.version())
                        .set(AssistantVersionEntity::getPublishedAt, version.publishedAt()))
                != 1) {
            throw new AssistantConcurrentModificationException();
        }
        requestMapper.insert(requestEntity(
                tenantId,
                PUBLISH_VERSION,
                keyHash,
                publication.requestFingerprint(),
                version.assistantId().value(),
                version.id().value(),
                version.publishedAt()));
        return new AssistantVersionPublicationOutcome(version, false);
    }

    private static Assistant toAssistant(AssistantEntity row) {
        return Assistant.reconstitute(
                new TenantId(row.getTenantId()),
                new AssistantId(row.getId()),
                row.getDisplayName(),
                row.getLatestVersionNumber(),
                row.getVersion(),
                new CreatorId(row.getCreatedBy()),
                row.getCreatedAt());
    }

    private static AssistantEntity toEntity(Assistant assistant) {
        var entity = new AssistantEntity();
        entity.setTenantId(assistant.tenantId().value());
        entity.setId(assistant.id().value());
        entity.setDisplayName(assistant.displayName());
        entity.setLatestVersionNumber(assistant.latestVersionNumber());
        entity.setVersion(assistant.version());
        entity.setCreatedBy(assistant.createdBy().value());
        entity.setCreatedAt(assistant.createdAt());
        return entity;
    }

    private static AssistantVersionEntity toVersionEntity(AssistantVersion version) {
        var entity = new AssistantVersionEntity();
        entity.setTenantId(version.tenantId().value());
        entity.setId(version.id().value());
        entity.setAssistantId(version.assistantId().value());
        entity.setVersionNumber(version.number());
        entity.setChatDeploymentId(version.chatDeploymentId());
        entity.setChatDeploymentRevision(version.chatDeploymentRevision());
        entity.setChatDeploymentOutputLimit(version.chatDeploymentOutputLimit());
        entity.setKnowledgeSpaceId(version.knowledgeSpaceId());
        entity.setIndexVersionId(version.indexVersionId());
        entity.setIndexVersionRevision(version.indexVersionRevision());
        entity.setEmbeddingDeploymentId(version.embeddingDeploymentId());
        entity.setEmbeddingDeploymentRevision(version.embeddingDeploymentRevision());
        entity.setEmbeddingDimension(version.embeddingDimension());
        entity.setSystemInstruction(version.systemInstruction());
        entity.setMaxOutputTokens(version.maxOutputTokens());
        entity.setRetrievalLimit(version.retrievalLimit());
        entity.setMinimumScore(version.minimumScore());
        entity.setCitationPolicy(version.citationPolicy().name());
        entity.setRefusalPolicy(version.refusalPolicy().name());
        entity.setStatus(version.status().name());
        entity.setVersion(version.version());
        entity.setCreatedBy(version.createdBy().value());
        entity.setCreatedAt(version.createdAt());
        entity.setPublishedAt(version.publishedAt());
        return entity;
    }

    private static LambdaQueryWrapper<AssistantVersionEntity> versionQuery(
            long tenantId,
            long versionId) {
        return new LambdaQueryWrapper<AssistantVersionEntity>()
                .eq(AssistantVersionEntity::getTenantId, tenantId)
                .eq(AssistantVersionEntity::getId, versionId);
    }

    private static AssistantVersion toVersion(AssistantVersionEntity row) {
        return AssistantVersion.reconstitute(
                new TenantId(row.getTenantId()),
                new AssistantVersionId(row.getId()),
                new AssistantId(row.getAssistantId()),
                row.getVersionNumber(),
                row.getChatDeploymentId(),
                row.getChatDeploymentRevision(),
                row.getChatDeploymentOutputLimit(),
                row.getKnowledgeSpaceId(),
                row.getIndexVersionId(),
                row.getIndexVersionRevision(),
                row.getEmbeddingDeploymentId(),
                row.getEmbeddingDeploymentRevision(),
                row.getEmbeddingDimension(),
                row.getSystemInstruction(),
                row.getMaxOutputTokens(),
                row.getRetrievalLimit(),
                row.getMinimumScore(),
                CitationPolicy.valueOf(row.getCitationPolicy()),
                RefusalPolicy.valueOf(row.getRefusalPolicy()),
                AssistantVersionStatus.valueOf(row.getStatus()),
                row.getVersion(),
                new CreatorId(row.getCreatedBy()),
                row.getCreatedAt(),
                row.getPublishedAt());
    }

    private static PublishedAssistantSummary toPublishedSummary(
            PublishedAssistantRow row) {
        return new PublishedAssistantSummary(
                Long.parseLong(row.getAssistantId()),
                row.getDisplayName(),
                Long.parseLong(row.getAssistantVersionId()),
                row.getVersionNumber(),
                row.getPublishedAt());
    }

    private static void requireFingerprint(
            AssistantRequestEntity row,
            String expected) {
        if (!row.getRequestFingerprint().equals(expected)) {
            throw new AssistantIdempotencyConflictException();
        }
    }

    private AssistantRequestEntity findRequest(
            long tenantId,
            String operation,
            String keyHash) {
        return requestMapper.selectOne(
                new LambdaQueryWrapper<AssistantRequestEntity>()
                        .eq(AssistantRequestEntity::getTenantId, tenantId)
                        .eq(AssistantRequestEntity::getOperation, operation)
                        .eq(
                                AssistantRequestEntity::getIdempotencyKeyHash,
                                keyHash));
    }

    private AssistantRequestEntity requestEntity(
            long tenantId,
            String operation,
            String keyHash,
            String requestFingerprint,
            long assistantId,
            Long assistantVersionId,
        java.time.Instant createdAt) {
        var entity = new AssistantRequestEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(tenantId);
        entity.setOperation(operation);
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(requestFingerprint);
        entity.setAssistantId(assistantId);
        entity.setAssistantVersionId(assistantVersionId);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

}
