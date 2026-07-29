package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * 已被知识管理员显式选定、可进入新索引选择的同步产物事实。
 *
 * <p>该事实冻结同步产物和质量结论，不保存正文或连接信息；阻断质量结论永远不能形成发布事实。</p>
 */
public final class PublishedSyncArtifact {

    private final SyncArtifact artifact;
    private final QualityStatus qualityStatus;
    private final String ruleSetVersion;
    private final Instant publishedAt;

    private PublishedSyncArtifact(
            SyncArtifact artifact, QualityStatus qualityStatus, String ruleSetVersion, Instant publishedAt) {
        this.artifact = Objects.requireNonNull(artifact, "同步产物不能为空");
        this.qualityStatus = Objects.requireNonNull(qualityStatus, "质量结论不能为空");
        this.ruleSetVersion = Objects.requireNonNull(ruleSetVersion, "质量规则版本不能为空");
        this.publishedAt = Objects.requireNonNull(publishedAt, "发布时间不能为空");
    }

    /**
     * 由同一不可变质量报告创建发布事实。
     *
     * @throws DataSourceStateException 质量结论阻断发布时抛出。
     */
    public static PublishedSyncArtifact create(
            SyncArtifact artifact, QualityReport report, Instant publishedAt) {
        Objects.requireNonNull(artifact, "同步产物不能为空");
        Objects.requireNonNull(report, "质量报告不能为空");
        report.requirePublishable();
        if (!artifact.id().equals(report.artifact().id())) {
            throw new IllegalArgumentException("质量报告必须归属于待发布同步产物");
        }
        return new PublishedSyncArtifact(artifact, report.status(), report.ruleSetVersion(), publishedAt);
    }

    public SyncArtifact artifact() { return artifact; }
    public QualityStatus qualityStatus() { return qualityStatus; }
    public String ruleSetVersion() { return ruleSetVersion; }
    public Instant publishedAt() { return publishedAt; }
}
