package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 一次同步产生的不可变来源事实。
 *
 * <p>产物冻结来源修订、私有规范化文本引用和记录数量，后续质量与索引只能引用该事实，不能读取可变连接配置。</p>
 */
public final class SyncArtifact implements AggregateRoot<SyncArtifactId> {

    private final SyncArtifactId id;
    private final TenantId tenantId;
    private final KnowledgeSpaceId knowledgeSpaceId;
    private final DataSourceId dataSourceId;
    private final long sourceRevision;
    private final TextContentReference content;
    private final long recordCount;
    private final Instant synchronizedAt;

    private SyncArtifact(
            SyncArtifactId id,
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId,
            long sourceRevision,
            TextContentReference content,
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
        this.content = Objects.requireNonNull(content, "同步产物内容引用不能为空");
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
            TextContentReference content,
            long recordCount,
            Instant synchronizedAt) {
        return new SyncArtifact(
                id,
                tenantId,
                knowledgeSpaceId,
                dataSourceId,
                sourceRevision,
                content,
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

    public TenantId tenantId() {
        return tenantId;
    }

    public KnowledgeSpaceId knowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public DataSourceId dataSourceId() {
        return dataSourceId;
    }

    /**
     * 返回冻结内容的 SHA-256 摘要。
     */
    public String contentDigest() {
        return content.sha256();
    }

    /**
     * 返回冻结的私有规范化文本引用。
     *
     * <p>索引只能读取此引用，不能回读可变来源定义或平台对象位置。</p>
     *
     * @return 不可变内容引用
     */
    public TextContentReference content() {
        return content;
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

}
