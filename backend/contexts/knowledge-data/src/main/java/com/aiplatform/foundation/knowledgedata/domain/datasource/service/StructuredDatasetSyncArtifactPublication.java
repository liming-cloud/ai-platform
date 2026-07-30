package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import java.util.Objects;

/** STRUCTURED_DATASET 发布后可由仓储原子提交的领域操作结果。 */
public final class StructuredDatasetSyncArtifactPublication implements DataSourceSyncArtifactPublication {
    private final long expectedSourceVersion; private final GovernedDataSource publishedSource; private final PublishedSyncArtifact publication;
    StructuredDatasetSyncArtifactPublication(long expectedSourceVersion, GovernedDataSource publishedSource, PublishedSyncArtifact publication) {
        if (expectedSourceVersion < 0) throw new IllegalArgumentException("预期来源修订不能为负数");
        this.expectedSourceVersion = expectedSourceVersion; this.publishedSource = Objects.requireNonNull(publishedSource, "已发布数据源不能为空"); this.publication = Objects.requireNonNull(publication, "发布事实不能为空");
        if (publishedSource.version() != expectedSourceVersion + 1 || !publishedSource.id().equals(publication.artifact().dataSourceId())) throw new IllegalArgumentException("结构化数据集发布结果不一致");
    }
    @Override public long expectedSourceVersion() { return expectedSourceVersion; }
    @Override public GovernedDataSource publishedSource() { return publishedSource; }
    @Override public PublishedSyncArtifact publication() { return publication; }
}
