package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import java.util.Objects;

/**
 * FILE 同步完成后可由仓储原子提交的不可变领域操作结果。
 *
 * <p>结果同时冻结来源的目标版本、同步产物和质量结论，使持久化层无需重演来源归属、修订或状态机判断。</p>
 */
public final class FileSynchronizationCompletion implements DataSourceSynchronizationCompletion {

    private final long expectedSourceVersion;
    private final GovernedDataSource completedSource;
    private final SyncArtifact artifact;
    private final QualityReport qualityReport;

    FileSynchronizationCompletion(
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
            throw new IllegalArgumentException("文件同步完成结果不一致");
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
