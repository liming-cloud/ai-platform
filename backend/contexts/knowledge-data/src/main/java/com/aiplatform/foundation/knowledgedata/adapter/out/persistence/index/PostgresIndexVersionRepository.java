package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.index;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.NoPublishedSourceException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.IdempotencyConflictException;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexVersionBuildRequest;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexVersionCreationOutcome;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexVersionRepository;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexVersionSlice;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.EmbeddingConfiguration;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersion;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionStatus;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 PostgreSQL 事务冻结来源快照并原子切换活动索引。
 */
public final class PostgresIndexVersionRepository implements IndexVersionRepository {

    private static final int MAX_CURSOR_LENGTH = 512;
    private static final String CREATE = "CREATE";
    private static final String ACTIVATE = "ACTIVATE";

    private final IndexVersionMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresIndexVersionRepository(
            IndexVersionMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "索引版本映射器不能为空");
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
    public IndexVersionSlice findPage(
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
        List<IndexVersionRow> rows;
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
                .map(PostgresIndexVersionRepository::toDomain)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getCreatedAt(),
                        Long.parseLong(pageRows.getLast().getId()))
                : null;
        return new IndexVersionSlice(items, nextCursor);
    }

    @Override
    public Optional<IndexVersion> findActive(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        return Optional.ofNullable(mapper.findActive(
                        tenantId.value(),
                        knowledgeSpaceId.value()))
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
        var replay = mapper.findRequest(tenantId, CREATE, keyHash);
        if (replay != null) {
            return replay(tenantId, replay, request.requestFingerprint());
        }

        var sources = mapper.findPublishedSources(tenantId, knowledgeSpaceId);
        if (sources.isEmpty()) {
            throw new NoPublishedSourceException();
        }
        var snapshotDigest = sourceSnapshotDigest(sources);
        var embedding = request.embedding();
        mapper.insertVersion(
                tenantId,
                knowledgeSpaceId,
                request.indexVersionId().value(),
                snapshotDigest,
                request.chunkPolicy(),
                embedding.deploymentId(),
                embedding.deploymentRevision(),
                embedding.dimension(),
                request.createdBy().value(),
                request.createdAt());
        for (var source : sources) {
            mapper.insertSource(
                    tenantId,
                    knowledgeSpaceId,
                    request.indexVersionId().value(),
                    Long.parseLong(source.getDataSourceId()),
                    source.getContentVersion(),
                    source.getObjectKey(),
                    source.getSha256(),
                    source.getByteSize(),
                    source.getMediaType(),
                    source.getContentCreatedAt());
        }
        mapper.insertJob(
                tenantId,
                knowledgeSpaceId,
                request.indexVersionId().value(),
                request.createdAt());
        mapper.insertRequest(
                tenantId,
                CREATE,
                keyHash,
                request.requestFingerprint(),
                knowledgeSpaceId,
                request.indexVersionId().value(),
                IndexVersionStatus.BUILDING.name(),
                0,
                request.createdAt());
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
        var replay = mapper.findRequest(tenantValue, ACTIVATE, keyHash);
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
        mapper.insertRequest(
                tenantValue,
                ACTIVATE,
                keyHash,
                requestFingerprint,
                spaceValue,
                id.value(),
                active.status().name(),
                active.version(),
                activatedAt);
        return new IndexVersionCreationOutcome(active, false);
    }

    private IndexVersionCreationOutcome replay(
            long tenantId,
            IndexVersionRequestRow request,
            String expectedFingerprint) {
        if (!request.getRequestFingerprint().equals(expectedFingerprint)) {
            throw new IdempotencyConflictException();
        }
        var row = mapper.findVersion(
                tenantId,
                Long.parseLong(request.getKnowledgeSpaceId()),
                Long.parseLong(request.getIndexVersionId()));
        if (row == null) {
            throw new IndexVersionNotFoundException();
        }
        return new IndexVersionCreationOutcome(toDomain(row), true);
    }

    static IndexVersion toDomain(IndexVersionRow row) {
        return IndexVersion.reconstitute(
                TenantId.parse(row.getTenantId()),
                KnowledgeSpaceId.parse(row.getKnowledgeSpaceId()),
                IndexVersionId.parse(row.getId()),
                row.getSourceSnapshotDigest(),
                row.getChunkPolicy(),
                new EmbeddingConfiguration(
                        row.getEmbeddingDeploymentId(),
                        row.getEmbeddingDeploymentRevision(),
                        row.getEmbeddingDimension()),
                IndexVersionStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getBuildDigest(),
                row.getChunkCount(),
                row.getFailureCode(),
                MembershipId.parse(row.getCreatedBy()),
                row.getCreatedAt(),
                row.getReadyAt(),
                row.getActivatedAt(),
                row.getSupersededAt());
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

    private static String encodeCursor(Instant createdAt, long id) {
        var raw = createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPosition decodeCursor(String cursor) {
        Objects.requireNonNull(cursor, "分页游标不能为空");
        if (cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH) {
            throw new IllegalArgumentException("分页游标格式不正确");
        }
        try {
            var raw = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            var separator = raw.indexOf('|');
            if (separator < 1 || separator == raw.length() - 1) {
                throw new IllegalArgumentException("分页游标格式不正确");
            }
            return new CursorPosition(
                    Instant.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("分页游标格式不正确", exception);
        }
    }

    private record CursorPosition(Instant createdAt, long id) {
    }
}
