package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import java.time.Instant;
import java.util.Objects;

/**
 * 收敛 FILE 同步产物发布与来源版本推进的跨聚合不变量。
 *
 * <p>发布不是同步成功的副作用；只有归属于当前 FILE 来源且质量非阻断的不可变产物，才能形成新的发布事实。</p>
 */
public final class FileSyncArtifactPublishing {

    /**
     * 显式发布 FILE 同步产物，并返回可被原子提交的领域操作结果。
     *
     * @throws DataSourceStateException 来源、产物、质量结论或状态不满足发布不变量时拒绝操作。
     */
    public FileSyncArtifactPublication publish(
            GovernedDataSource source,
            SyncArtifact artifact,
            QualityReport report,
            Instant publishedAt) {
        var fileSource = requireFileSource(source);
        var sourceArtifact = requireCurrentArtifact(fileSource, artifact);
        var qualityReport = Objects.requireNonNull(report, "质量结论不能为空");
        if (!sourceArtifact.id().equals(qualityReport.artifact().id())) {
            throw new DataSourceStateException("质量结论不属于待发布同步产物");
        }
        var publication = PublishedSyncArtifact.create(
                sourceArtifact, qualityReport, Objects.requireNonNull(publishedAt, "发布时间不能为空"));
        var publishedSource = fileSource.publishArtifact(fileSource.version());
        return new FileSyncArtifactPublication(fileSource.version(), publishedSource, publication, qualityReport);
    }

    private static GovernedDataSource requireFileSource(GovernedDataSource source) {
        var value = Objects.requireNonNull(source, "受治理数据源不能为空");
        if (value.type() != GovernedDataSourceType.FILE) {
            throw new DataSourceStateException("当前数据源不是 FILE 类型");
        }
        return value;
    }

    private static SyncArtifact requireCurrentArtifact(GovernedDataSource source, SyncArtifact artifact) {
        var value = Objects.requireNonNull(artifact, "同步产物不能为空");
        if (!source.tenantId().equals(value.tenantId())
                || !source.knowledgeSpaceId().equals(value.knowledgeSpaceId())
                || !source.id().equals(value.dataSourceId())
                || source.currentRevision() != value.sourceRevision()) {
            throw new DataSourceStateException("同步产物不属于来源当前修订");
        }
        return value;
    }
}
