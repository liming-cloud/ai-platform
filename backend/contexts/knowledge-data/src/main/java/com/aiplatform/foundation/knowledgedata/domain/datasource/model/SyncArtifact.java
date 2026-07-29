package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.domain.Entity;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次同步产生的不可变来源事实。
 *
 * <p>产物冻结来源修订、内容摘要和记录数量，后续质量与索引只能引用该事实，不能读取可变连接配置。</p>
 */
public final class SyncArtifact implements Entity<SyncArtifactId> {

    private final SyncArtifactId id;
    private final TenantId tenantId;
    private final KnowledgeSpaceId knowledgeSpaceId;
    private final DataSourceId dataSourceId;
    private final long sourceRevision;
    private final String contentDigest;
    private final long recordCount;
    private final Instant synchronizedAt;

    private SyncArtifact(
            SyncArtifactId id,
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long sourceRevision,
            String contentDigest,
            long recordCount,
            Instant synchronizedAt) {
        this.id = Objects.requireNonNull(id, "同步产物标识不能为空");
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.knowledgeSpaceId = Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        this.dataSourceId = Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("来源修订必须为正数");
        }
        this.sourceRevision = sourceRevision;
        this.contentDigest = requireDigest(contentDigest);
        if (recordCount < 0) {
            throw new IllegalArgumentException("同步记录数不能为负数");
        }
        this.recordCount = recordCount;
        this.synchronizedAt = Objects.requireNonNull(synchronizedAt, "同步时间不能为空");
    }

    /**
     * 创建已完成同步的不可变产物。
     */
    public static SyncArtifact create(
            SyncArtifactId id,
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long sourceRevision,
            String contentDigest,
            long recordCount,
            Instant synchronizedAt) {
        return new SyncArtifact(
                id,
                tenantId,
                knowledgeSpaceId,
                dataSourceId,
                sourceRevision,
                contentDigest,
                recordCount,
                synchronizedAt);
    }

    /**
     * 返回同步产物标识。
     */
    @Override
    public SyncArtifactId id() {
        return id;
    }

    /**
     * 返回生成此产物的来源定义修订。
     */
    public long sourceRevision() {
        return sourceRevision;
    }

    /**
     * 返回冻结内容的 SHA-256 摘要。
     */
    public String contentDigest() {
        return contentDigest;
    }

    /**
     * 返回同步结果中的记录数量。
     */
    public long recordCount() {
        return recordCount;
    }

    /**
     * 返回产物完成同步的时间。
     */
    public Instant synchronizedAt() {
        return synchronizedAt;
    }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64
                || value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("同步内容摘要必须是 64 位十六进制 SHA-256");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
