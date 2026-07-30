package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import java.util.Objects;

/**
 * STRUCTURED_DATASET 同步完成后可由仓储原子提交的不可变领域操作结果。
 *
 * <p>结果固定目标来源版本、产物与质量结论，持久化层不得重新推导 CSV 行过滤或来源状态机。</p>
 */
public final class StructuredDatasetCsvSynchronizationCompletion {

    private final long expectedSourceVersion;
    private final GovernedDataSource completedSource;
    private final SyncArtifact artifact;
    private final QualityReport qualityReport;

    StructuredDatasetCsvSynchronizationCompletion(
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
            throw new IllegalArgumentException("结构化数据集同步完成结果不一致");
        }
    }

    /** 返回提交前必须匹配的来源乐观版本。 */
    public long expectedSourceVersion() {
        return expectedSourceVersion;
    }

    /** 返回原子提交后的已就绪来源。 */
    public GovernedDataSource completedSource() {
        return completedSource;
    }

    /** 返回本次同步生成的不可变产物。 */
    public SyncArtifact artifact() {
        return artifact;
    }

    /** 返回与产物绑定的不可变质量结论。 */
    public QualityReport qualityReport() {
        return qualityReport;
    }
}
