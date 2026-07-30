package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import java.util.Objects;

/** OBJECT_STORAGE 同步完成后可由仓储原子提交的不可变领域操作结果。 */
public final class ObjectStorageSynchronizationCompletion implements DataSourceSynchronizationCompletion {

    private final long expectedSourceVersion;
    private final GovernedDataSource completedSource;
    private final SyncArtifact artifact;
    private final QualityReport qualityReport;

    ObjectStorageSynchronizationCompletion(
            long expectedSourceVersion,
            GovernedDataSource completedSource,
            SyncArtifact artifact,
            QualityReport qualityReport) {
        if (expectedSourceVersion < 0) {
            throw new IllegalArgumentException("预期来源修订不能为负数");
        }
        this.expectedSourceVersion = expectedSourceVersion;
        this.completedSource = Objects.requireNonNull(completedSource, "完成同步的数据源不能为空");
        this.artifact = Objects.requireNonNull(artifact, "同步产物不能为空");
        this.qualityReport = Objects.requireNonNull(qualityReport, "质量结论不能为空");
        if (completedSource.status() != GovernedDataSourceStatus.READY
                || completedSource.version() != expectedSourceVersion + 1
                || !completedSource.tenantId().equals(artifact.tenantId())
                || !completedSource.knowledgeSpaceId().equals(artifact.knowledgeSpaceId())
                || !completedSource.id().equals(artifact.dataSourceId())
                || completedSource.currentRevision() != artifact.sourceRevision()
                || !artifact.id().equals(qualityReport.artifact().id())) {
            throw new IllegalArgumentException("对象存储同步完成结果不一致");
        }
    }

    @Override
    public long expectedSourceVersion() {
        return expectedSourceVersion;
    }

    @Override
    public GovernedDataSource completedSource() {
        return completedSource;
    }

    @Override
    public SyncArtifact artifact() {
        return artifact;
    }

    @Override
    public QualityReport qualityReport() {
        return qualityReport;
    }
}
