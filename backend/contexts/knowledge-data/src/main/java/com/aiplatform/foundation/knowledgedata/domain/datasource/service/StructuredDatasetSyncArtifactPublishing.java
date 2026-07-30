package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import java.time.Instant;
import java.util.Objects;

/** 收敛 STRUCTURED_DATASET 同步产物发布与来源版本推进的不变量。 */
public final class StructuredDatasetSyncArtifactPublishing {
    public StructuredDatasetSyncArtifactPublication publish(GovernedDataSource source, SyncArtifact artifact, QualityReport report, Instant publishedAt) {
        var dataset = Objects.requireNonNull(source, "受治理数据源不能为空");
        if (dataset.type() != GovernedDataSourceType.STRUCTURED_DATASET) throw new DataSourceStateException("当前数据源不是 STRUCTURED_DATASET 类型");
        var value = Objects.requireNonNull(artifact, "同步产物不能为空"); var quality = Objects.requireNonNull(report, "质量结论不能为空");
        if (!dataset.tenantId().equals(value.tenantId()) || !dataset.knowledgeSpaceId().equals(value.knowledgeSpaceId()) || !dataset.id().equals(value.dataSourceId()) || dataset.currentRevision() != value.sourceRevision() || !value.id().equals(quality.artifact().id())) throw new DataSourceStateException("结构化数据集产物或质量结论不属于当前来源修订");
        var publication = PublishedSyncArtifact.create(value, quality, Objects.requireNonNull(publishedAt, "发布时间不能为空"));
        return new StructuredDatasetSyncArtifactPublication(dataset.version(), dataset.publishArtifact(dataset.version()), publication);
    }
}
