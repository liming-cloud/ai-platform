package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import java.util.Objects;

/**
 * FILE 同步产物发布后可由仓储原子提交的不可变领域操作结果。
 *
 * <p>结果冻结来源目标版本、发布事实与同一质量结论，持久化层只需在同一事务中更新来源并写入发布记录。</p>
 */
public final class FileSyncArtifactPublication {

    private final long expectedSourceVersion;
    private final GovernedDataSource publishedSource;
    private final PublishedSyncArtifact publication;
    private final QualityReport qualityReport;

    FileSyncArtifactPublication(
            long expectedSourceVersion,
            GovernedDataSource publishedSource,
            PublishedSyncArtifact publication,
            QualityReport qualityReport) {
        if (expectedSourceVersion < 0) {
            throw new IllegalArgumentException("预期来源修订不能为负数");
        }
        this.expectedSourceVersion = expectedSourceVersion;
        this.publishedSource = Objects.requireNonNull(publishedSource, "发布后的数据源不能为空");
        this.publication = Objects.requireNonNull(publication, "发布产物不能为空");
        this.qualityReport = Objects.requireNonNull(qualityReport, "质量结论不能为空");
        var artifact = publication.artifact();
        if (publishedSource.status() != GovernedDataSourceStatus.READY
                || publishedSource.version() != expectedSourceVersion + 1
                || !publishedSource.tenantId().equals(artifact.tenantId())
                || !publishedSource.knowledgeSpaceId().equals(artifact.knowledgeSpaceId())
                || !publishedSource.id().equals(artifact.dataSourceId())
                || publishedSource.currentRevision() != artifact.sourceRevision()
                || !artifact.id().equals(qualityReport.artifact().id())
                || publication.qualityStatus() != qualityReport.status()
                || !publication.ruleSetVersion().equals(qualityReport.ruleSetVersion())) {
            throw new IllegalArgumentException("文件同步产物发布结果不一致");
        }
    }

    public long expectedSourceVersion() {
        return expectedSourceVersion;
    }

    public GovernedDataSource publishedSource() {
        return publishedSource;
    }

    public PublishedSyncArtifact publication() {
        return publication;
    }

    public QualityReport qualityReport() {
        return qualityReport;
    }
}
