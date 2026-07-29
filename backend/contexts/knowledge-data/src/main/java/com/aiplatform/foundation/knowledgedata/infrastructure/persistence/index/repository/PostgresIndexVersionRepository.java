package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.knowledgedata.application.indexversion.exception.IndexVersionConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.exception.IndexVersionNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.exception.NoPublishedSourceException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionCreationOutcome;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.repository.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersionStatus;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexBuildJobMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexSourceMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.mapper.IndexVersionRequestMapper;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity.IndexBuildJobEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity.IndexSourceEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity.IndexVersionEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity.IndexVersionRequestEntity;
import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.projection.IndexSourceRow;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务冻结来源快照并原子切换活动索引。
 */
public final class PostgresIndexVersionRepository implements IndexVersionRepository {

    private static final String CREATE = "CREATE";
    private static final String ACTIVATE = "ACTIVATE";

    private final IndexVersionMapper mapper;
    private final IndexSourceMapper sourceMapper;
    private final IndexBuildJobMapper jobMapper;
    private final IndexVersionRequestMapper requestMapper;
    private final EntityIdGenerator identifiers;
    private final TransactionTemplate transactions;

    public PostgresIndexVersionRepository(
            IndexVersionMapper mapper,
            IndexSourceMapper sourceMapper,
            IndexBuildJobMapper jobMapper,
            IndexVersionRequestMapper requestMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "索引版本映射器不能为空");
        this.sourceMapper = Objects.requireNonNull(sourceMapper, "索引来源映射器不能为空");
        this.jobMapper = Objects.requireNonNull(jobMapper, "索引构建作业映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "索引版本幂等请求映射器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public IndexVersionCreationOutcome create(IndexVersionBuildRequest value) {
        Objects.requireNonNull(value, "索引版本构建请求不能为空");
        var result = transactions.execute(status -> createInTransaction(value));
        return Objects.requireNonNull(result, "索引版本创建事务没有返回结果");
    }

    @Override
    public IndexVersionPage findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            long pageNumber,
            long pageSize) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        var page = mapper.selectPage(
                new Page<IndexVersionEntity>(pageNumber, pageSize, true),
                Wrappers.<IndexVersionEntity>lambdaQuery()
                        .eq(IndexVersionEntity::getTenantId, tenantId.value())
                        .eq(
                                IndexVersionEntity::getKnowledgeSpaceId,
                                knowledgeSpaceId.value())
                        .orderByDesc(
                                IndexVersionEntity::getCreatedAt,
                                IndexVersionEntity::getId));
        return new IndexVersionPage(
                page.getRecords().stream()
                        .map(PostgresIndexVersionRepository::toDomain)
                        .toList(),
                pageNumber,
                pageSize,
                page.getTotal());
    }

    @Override
    public Optional<IndexVersion> findActive(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        return Optional.ofNullable(mapper.selectOne(
                        Wrappers.<IndexVersionEntity>lambdaQuery()
                                .eq(IndexVersionEntity::getTenantId, tenantId.value())
                                .eq(
                                        IndexVersionEntity::getKnowledgeSpaceId,
                                        knowledgeSpaceId.value())
                                .eq(IndexVersionEntity::getStatus, "ACTIVE")))
                .map(PostgresIndexVersionRepository::toDomain);
    }

    @Override
    public IndexVersionCreationOutcome activate(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint,
            Instant activatedAt) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(id, "索引版本标识不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        Objects.requireNonNull(activatedAt, "激活时间不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期修订不能为负数");
        }
        var result = transactions.execute(status -> activateInTransaction(
                tenantId,
                knowledgeSpaceId,
                id,
                expectedVersion,
                idempotencyKey,
                requestFingerprint,
                activatedAt));
        return Objects.requireNonNull(result, "索引激活事务没有返回结果");
    }

    private IndexVersionCreationOutcome createInTransaction(
            IndexVersionBuildRequest request) {
        var tenantId = request.tenantId().value();
        var knowledgeSpaceId = request.knowledgeSpaceId().value();
        mapper.lockSpace(lockScope(tenantId, knowledgeSpaceId));
        var keyHash = sha256(request.idempotencyKey());
        var replay = findRequest(tenantId, CREATE, keyHash);
        if (replay != null) {
            return replay(tenantId, replay, request.requestFingerprint());
        }

        var sources = mapper.findPublishedSources(tenantId, knowledgeSpaceId);
        if (sources.isEmpty()) {
            throw new NoPublishedSourceException();
        }
        var snapshotDigest = sourceSnapshotDigest(sources);
        var embedding = request.embedding();
        mapper.insert(toEntity(request, snapshotDigest));
        for (var source : sources) {
            sourceMapper.insert(toSourceEntity(request, source));
        }
        jobMapper.insert(toJobEntity(request));
        requestMapper.insert(toRequestEntity(
                tenantId,
                CREATE,
                keyHash,
                request.requestFingerprint(),
                knowledgeSpaceId,
                request.indexVersionId().value(),
                IndexVersionStatus.BUILDING,
                0,
                request.createdAt()));
        var version = IndexVersion.start(
                request.tenantId(),
                request.knowledgeSpaceId(),
                request.indexVersionId(),
                snapshotDigest,
                request.chunkPolicy(),
                embedding,
                request.createdBy(),
                request.createdAt());
        return new IndexVersionCreationOutcome(version, false);
    }

    private IndexVersionCreationOutcome activateInTransaction(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            long expectedVersion,
            String idempotencyKey,
            String requestFingerprint,
            Instant activatedAt) {
        var tenantValue = tenantId.value();
        var spaceValue = knowledgeSpaceId.value();
        mapper.lockSpace(lockScope(tenantValue, spaceValue));
        var keyHash = sha256(idempotencyKey);
        var replay = findRequest(tenantValue, ACTIVATE, keyHash);
        if (replay != null) {
            return replay(tenantValue, replay, requestFingerprint);
        }
        var currentRow = mapper.findVersionForUpdate(
                tenantValue,
                spaceValue,
                id.value());
        if (currentRow == null) {
            throw new IndexVersionNotFoundException();
        }
        var current = toDomain(currentRow);
        if (current.status() != IndexVersionStatus.READY
                || current.version() != expectedVersion) {
            throw new IndexVersionConcurrentModificationException();
        }

        mapper.supersedeActive(tenantValue, spaceValue, activatedAt);
        if (mapper.activateReady(
                tenantValue,
                spaceValue,
                id.value(),
                expectedVersion,
                activatedAt) != 1) {
            throw new IndexVersionConcurrentModificationException();
        }
        var active = current.activate(activatedAt);
        requestMapper.insert(toRequestEntity(
                tenantValue,
                ACTIVATE,
                keyHash,
                requestFingerprint,
                spaceValue,
                id.value(),
                active.status(),
                active.version(),
                activatedAt));
        return new IndexVersionCreationOutcome(active, false);
    }

    private IndexVersionCreationOutcome replay(
            long tenantId,
            IndexVersionRequestEntity request,
            String expectedFingerprint) {
        if (!request.getRequestFingerprint().equals(expectedFingerprint)) {
            throw new IdempotencyConflictException();
        }
        var row = mapper.selectOne(versionQuery(
                tenantId,
                request.getKnowledgeSpaceId(),
                request.getIndexVersionId()));
        if (row == null) {
            throw new IndexVersionNotFoundException();
        }
        return new IndexVersionCreationOutcome(toDomain(row), true);
    }

    static IndexVersion toDomain(IndexVersionEntity row) {
        return IndexVersion.reconstitute(
                new TenantId(row.getTenantId()),
                new KnowledgeSpaceId(row.getKnowledgeSpaceId()),
                new IndexVersionId(row.getId()),
                row.getSourceSnapshotDigest(),
                row.getChunkPolicy(),
                embedding(row),
                IndexVersionStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getBuildDigest(),
                row.getChunkCount(),
                row.getFailureCode(),
                new CreatorId(row.getCreatedBy()),
                row.getCreatedAt(),
                row.getReadyAt(),
                row.getActivatedAt(),
                row.getSupersededAt());
    }

    private IndexVersionRequestEntity findRequest(
            long tenantId,
            String operation,
            String keyHash) {
        return requestMapper.selectOne(
                Wrappers.<IndexVersionRequestEntity>lambdaQuery()
                        .eq(IndexVersionRequestEntity::getTenantId, tenantId)
                        .eq(IndexVersionRequestEntity::getOperation, operation)
                        .eq(IndexVersionRequestEntity::getIdempotencyKeyHash, keyHash));
    }

    static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
            IndexVersionEntity> versionQuery(
                    long tenantId,
                    long knowledgeSpaceId,
                    long indexVersionId) {
        return Wrappers.<IndexVersionEntity>lambdaQuery()
                .eq(IndexVersionEntity::getTenantId, tenantId)
                .eq(IndexVersionEntity::getKnowledgeSpaceId, knowledgeSpaceId)
                .eq(IndexVersionEntity::getId, indexVersionId);
    }

    private static IndexVersionEntity toEntity(
            IndexVersionBuildRequest request,
            String snapshotDigest) {
        var entity = new IndexVersionEntity();
        entity.setId(request.indexVersionId().value());
        entity.setTenantId(request.tenantId().value());
        entity.setKnowledgeSpaceId(request.knowledgeSpaceId().value());
        entity.setSourceSnapshotDigest(snapshotDigest);
        entity.setChunkPolicy(request.chunkPolicy());
        entity.setEmbeddingDeploymentId(request.embedding().deploymentId());
        entity.setEmbeddingDeploymentRevision(request.embedding().deploymentRevision());
        entity.setEmbeddingConfigurationId(request.embedding().configurationId());
        entity.setEmbeddingConfigurationRevision(request.embedding().configurationRevision());
        entity.setEmbeddingCatalogEntryId(request.embedding().catalogEntryId());
        entity.setEmbeddingCatalogEntryRevision(request.embedding().catalogEntryRevision());
        entity.setEmbeddingDimension(request.embedding().dimension());
        entity.setStatus(IndexVersionStatus.BUILDING.name());
        entity.setVersion(0);
        entity.setChunkCount(0);
        entity.setCreatedBy(request.createdBy().value());
        entity.setCreatedAt(request.createdAt());
        return entity;
    }

    private static EmbeddingConfiguration embedding(IndexVersionEntity row) {
        if (row.getEmbeddingConfigurationId() != null) {
            return EmbeddingConfiguration.governed(
                    row.getEmbeddingConfigurationId(),
                    row.getEmbeddingConfigurationRevision(),
                    row.getEmbeddingCatalogEntryId(),
                    row.getEmbeddingCatalogEntryRevision(),
                    row.getEmbeddingDimension());
        }
        return new EmbeddingConfiguration(
                row.getEmbeddingDeploymentId(),
                row.getEmbeddingDeploymentRevision(),
                row.getEmbeddingDimension());
    }

    private IndexSourceEntity toSourceEntity(
            IndexVersionBuildRequest request,
            IndexSourceRow source) {
        var entity = new IndexSourceEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(request.tenantId().value());
        entity.setKnowledgeSpaceId(request.knowledgeSpaceId().value());
        entity.setIndexVersionId(request.indexVersionId().value());
        entity.setDataSourceId(Long.parseLong(source.getDataSourceId()));
        entity.setContentVersion(source.getContentVersion());
        entity.setSourceType("LEGACY_TEXT_CONTENT");
        entity.setSourceRevision(source.getContentVersion());
        entity.setObjectKey(source.getObjectKey());
        entity.setSha256(source.getSha256());
        entity.setByteSize(source.getByteSize());
        entity.setMediaType(source.getMediaType());
        entity.setContentCreatedAt(source.getContentCreatedAt());
        return entity;
    }

    private static IndexBuildJobEntity toJobEntity(IndexVersionBuildRequest request) {
        var entity = new IndexBuildJobEntity();
        entity.setId(request.indexVersionId().value());
        entity.setTenantId(request.tenantId().value());
        entity.setKnowledgeSpaceId(request.knowledgeSpaceId().value());
        entity.setIndexVersionId(request.indexVersionId().value());
        entity.setStatus("PENDING");
        entity.setAttempt(0);
        entity.setAvailableAt(request.createdAt());
        entity.setCreatedAt(request.createdAt());
        return entity;
    }

    private IndexVersionRequestEntity toRequestEntity(
            long tenantId,
            String operation,
            String keyHash,
            String fingerprint,
            long knowledgeSpaceId,
            long indexVersionId,
            IndexVersionStatus status,
            long version,
            Instant createdAt) {
        var entity = new IndexVersionRequestEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(tenantId);
        entity.setOperation(operation);
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(fingerprint);
        entity.setKnowledgeSpaceId(knowledgeSpaceId);
        entity.setIndexVersionId(indexVersionId);
        entity.setResultStatus(status.name());
        entity.setResultVersion(version);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private static String sourceSnapshotDigest(List<IndexSourceRow> sources) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var source : sources) {
                update(digest, source.getDataSourceId());
                update(digest, Integer.toString(source.getContentVersion()));
                update(digest, source.getSha256());
                update(digest, Long.toString(source.getByteSize()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
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

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String lockScope(long tenantId, long knowledgeSpaceId) {
        return tenantId + ":" + knowledgeSpaceId;
    }

}
