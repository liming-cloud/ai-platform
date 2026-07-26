package com.aiplatform.foundation.knowledgedata.domain.indexversion;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 固定来源快照、分块语义和嵌入配置的不可变索引版本。
 */
public final class IndexVersion {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern POLICY = Pattern.compile("[a-z][a-z0-9-]{2,63}");
    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,95}");

    private final TenantId tenantId;
    private final KnowledgeSpaceId knowledgeSpaceId;
    private final IndexVersionId id;
    private final String sourceSnapshotDigest;
    private final String chunkPolicy;
    private final EmbeddingConfiguration embedding;
    private final IndexVersionStatus status;
    private final long version;
    private final String buildDigest;
    private final int chunkCount;
    private final String failureCode;
    private final MembershipId createdBy;
    private final Instant createdAt;
    private final Instant readyAt;
    private final Instant activatedAt;
    private final Instant supersededAt;

    private IndexVersion(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            String sourceSnapshotDigest,
            String chunkPolicy,
            EmbeddingConfiguration embedding,
            IndexVersionStatus status,
            long version,
            String buildDigest,
            int chunkCount,
            String failureCode,
            MembershipId createdBy,
            Instant createdAt,
            Instant readyAt,
            Instant activatedAt,
            Instant supersededAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(
                knowledgeSpaceId,
                "知识空间标识不能为空");
        this.id = Objects.requireNonNull(id, "索引版本标识不能为空");
        this.sourceSnapshotDigest = requireDigest(
                sourceSnapshotDigest,
                "来源快照摘要");
        this.chunkPolicy = requirePolicy(chunkPolicy);
        this.embedding = Objects.requireNonNull(embedding, "嵌入配置不能为空");
        this.status = Objects.requireNonNull(status, "索引版本状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("索引版本修订不能为负数");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("索引分块数量不能为负数");
        }
        this.version = version;
        this.buildDigest = optionalDigest(buildDigest);
        this.chunkCount = chunkCount;
        this.failureCode = optionalFailureCode(failureCode);
        this.createdBy = Objects.requireNonNull(createdBy, "创建成员不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.readyAt = readyAt;
        this.activatedAt = activatedAt;
        this.supersededAt = supersededAt;
        verifyState();
    }

    public static IndexVersion start(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            String sourceSnapshotDigest,
            String chunkPolicy,
            EmbeddingConfiguration embedding,
            MembershipId createdBy,
            Instant createdAt) {
        return new IndexVersion(
                tenantId,
                knowledgeSpaceId,
                id,
                sourceSnapshotDigest,
                chunkPolicy,
                embedding,
                IndexVersionStatus.BUILDING,
                0,
                null,
                0,
                null,
                createdBy,
                createdAt,
                null,
                null,
                null);
    }

    public static IndexVersion reconstitute(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            IndexVersionId id,
            String sourceSnapshotDigest,
            String chunkPolicy,
            EmbeddingConfiguration embedding,
            IndexVersionStatus status,
            long version,
            String buildDigest,
            int chunkCount,
            String failureCode,
            MembershipId createdBy,
            Instant createdAt,
            Instant readyAt,
            Instant activatedAt,
            Instant supersededAt) {
        return new IndexVersion(
                tenantId,
                knowledgeSpaceId,
                id,
                sourceSnapshotDigest,
                chunkPolicy,
                embedding,
                status,
                version,
                buildDigest,
                chunkCount,
                failureCode,
                createdBy,
                createdAt,
                readyAt,
                activatedAt,
                supersededAt);
    }

    public IndexVersion markReady(String digest, int chunks, Instant completedAt) {
        requireStatus(IndexVersionStatus.BUILDING, "只有构建中的索引才能进入就绪状态");
        requireDigest(digest, "构建摘要");
        if (chunks < 1) {
            throw new IllegalArgumentException("就绪索引必须包含分块");
        }
        return copy(
                IndexVersionStatus.READY,
                version + 1,
                digest,
                chunks,
                null,
                Objects.requireNonNull(completedAt, "就绪时间不能为空"),
                null,
                null);
    }

    public IndexVersion activate(Instant time) {
        requireStatus(IndexVersionStatus.READY, "只有就绪索引才能激活");
        return copy(
                IndexVersionStatus.ACTIVE,
                version + 1,
                buildDigest,
                chunkCount,
                null,
                readyAt,
                Objects.requireNonNull(time, "激活时间不能为空"),
                null);
    }

    public IndexVersion supersede(Instant time) {
        requireStatus(IndexVersionStatus.ACTIVE, "只有活动索引才能被替代");
        return copy(
                IndexVersionStatus.SUPERSEDED,
                version + 1,
                buildDigest,
                chunkCount,
                null,
                readyAt,
                activatedAt,
                Objects.requireNonNull(time, "替代时间不能为空"));
    }

    public IndexVersion fail(String code) {
        requireStatus(IndexVersionStatus.BUILDING, "只有构建中的索引才能失败");
        return copy(
                IndexVersionStatus.FAILED,
                version + 1,
                null,
                0,
                requireFailureCode(code),
                null,
                null,
                null);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public KnowledgeSpaceId knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public IndexVersionId id() {
        return id;
    }

    public String sourceSnapshotDigest() {
        return sourceSnapshotDigest;
    }

    public String chunkPolicy() {
        return chunkPolicy;
    }

    public EmbeddingConfiguration embedding() {
        return embedding;
    }

    public IndexVersionStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public String buildDigest() {
        return buildDigest;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public String failureCode() {
        return failureCode;
    }

    public MembershipId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant readyAt() {
        return readyAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant supersededAt() {
        return supersededAt;
    }

    private IndexVersion copy(
            IndexVersionStatus nextStatus,
            long nextVersion,
            String nextBuildDigest,
            int nextChunkCount,
            String nextFailureCode,
            Instant nextReadyAt,
            Instant nextActivatedAt,
            Instant nextSupersededAt) {
        return new IndexVersion(
                tenantId,
                knowledgeSpaceId,
                id,
                sourceSnapshotDigest,
                chunkPolicy,
                embedding,
                nextStatus,
                nextVersion,
                nextBuildDigest,
                nextChunkCount,
                nextFailureCode,
                createdBy,
                createdAt,
                nextReadyAt,
                nextActivatedAt,
                nextSupersededAt);
    }

    private void requireStatus(IndexVersionStatus expected, String message) {
        if (status != expected) {
            throw new IndexVersionStateException(message);
        }
    }

    private void verifyState() {
        switch (status) {
            case BUILDING -> requireState(
                    buildDigest == null
                            && chunkCount == 0
                            && failureCode == null
                            && readyAt == null
                            && activatedAt == null
                            && supersededAt == null);
            case READY -> requireState(
                    buildDigest != null
                            && chunkCount > 0
                            && failureCode == null
                            && readyAt != null
                            && activatedAt == null
                            && supersededAt == null);
            case ACTIVE -> requireState(
                    buildDigest != null
                            && chunkCount > 0
                            && failureCode == null
                            && readyAt != null
                            && activatedAt != null
                            && supersededAt == null);
            case SUPERSEDED -> requireState(
                    buildDigest != null
                            && chunkCount > 0
                            && failureCode == null
                            && readyAt != null
                            && activatedAt != null
                            && supersededAt != null);
            case FAILED -> requireState(
                    buildDigest == null
                            && chunkCount == 0
                            && failureCode != null
                            && readyAt == null
                            && activatedAt == null
                            && supersededAt == null);
        }
    }

    private static void requireState(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("索引版本状态字段不一致");
        }
    }

    private static String requireDigest(String value, String field) {
        Objects.requireNonNull(value, field + "不能为空");
        if (!SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "格式不正确");
        }
        return value;
    }

    private static String optionalDigest(String value) {
        return value == null ? null : requireDigest(value, "构建摘要");
    }

    private static String requirePolicy(String value) {
        Objects.requireNonNull(value, "分块策略不能为空");
        if (!POLICY.matcher(value).matches()) {
            throw new IllegalArgumentException("分块策略格式不正确");
        }
        return value;
    }

    private static String requireFailureCode(String value) {
        Objects.requireNonNull(value, "失败码不能为空");
        if (!FAILURE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("失败码格式不正确");
        }
        return value;
    }

    private static String optionalFailureCode(String value) {
        return value == null ? null : requireFailureCode(value);
    }
}
