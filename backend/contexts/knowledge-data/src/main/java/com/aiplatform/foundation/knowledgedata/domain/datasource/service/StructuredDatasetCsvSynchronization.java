package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetRowFilter;
import java.time.Instant;
import java.util.Objects;

/**
 * 收敛 STRUCTURED_DATASET 同步状态、行过滤质量和不可变产物的跨聚合不变量。
 *
 * <p>调用方只可传入事务外 CSV 解析产生的摘要事实；本服务不读取私有对象、不解析 CSV、不鉴权也不依赖持久化技术。</p>
 */
public final class StructuredDatasetCsvSynchronization {

    private static final String QUALITY_RULE_SET_VERSION = "dataset-csv-v1";

    /**
     * 开始或仅按同步失败语义恢复结构化数据集同步。
     *
     * @throws DataSourceStateException 来源类型、状态或修订不允许同步时抛出。
     */
    public GovernedDataSource begin(GovernedDataSource source, long expectedVersion) {
        var dataset = requireStructuredDataset(source);
        if (dataset.status() != GovernedDataSourceStatus.FAILED) {
            return dataset.startSynchronization(expectedVersion);
        }
        if (dataset.failedOperation() != DataSourceOperation.SYNCHRONIZATION) {
            throw new DataSourceStateException("当前失败操作不允许重新同步");
        }
        return dataset.retry(expectedVersion);
    }

    /**
     * 以当前结构化数据集修订完成同步，并以行过滤摘要创建不可变质量结论。
     *
     * @throws DataSourceStateException 来源、修订、状态、有效行数或行过滤摘要不满足不变量时抛出。
     */
    public StructuredDatasetCsvSynchronizationCompletion complete(
            GovernedDataSource source,
            SourceRevision revision,
            SyncArtifactId artifactId,
            TextContentReference content,
            long recordCount,
            long skippedBlankRowCount,
            Instant synchronizedAt) {
        var syncingSource = requireStructuredDataset(source);
        var currentRevision = requireCurrentRevision(syncingSource, revision);
        if (recordCount < 1) {
            throw new DataSourceStateException("结构化数据集同步必须至少包含一条有效记录");
        }
        if (skippedBlankRowCount < 0) {
            throw new IllegalArgumentException("跳过空内容行数不能为负数");
        }
        if (currentRevision.structuredDataset().rowFilter() != StructuredDatasetRowFilter.NON_BLANK_CONTENT
                && skippedBlankRowCount != 0) {
            throw new DataSourceStateException("当前行过滤策略不允许跳过空内容行");
        }
        requireNormalizedContent(currentRevision, content);
        var artifact = SyncArtifact.create(
                Objects.requireNonNull(artifactId, "同步产物标识不能为空"),
                syncingSource.tenantId(),
                syncingSource.knowledgeSpaceId(),
                syncingSource.id(),
                currentRevision.revision(),
                content,
                recordCount,
                Objects.requireNonNull(synchronizedAt, "同步时间不能为空"));
        var report = skippedBlankRowCount == 0
                ? QualityReport.passed(artifact, QUALITY_RULE_SET_VERSION)
                : QualityReport.warned(artifact, QUALITY_RULE_SET_VERSION, skippedBlankRowCount);
        var completedSource = syncingSource.completeSynchronization(syncingSource.version());
        return new StructuredDatasetCsvSynchronizationCompletion(
                syncingSource.version(), completedSource, artifact, report);
    }

    private static GovernedDataSource requireStructuredDataset(GovernedDataSource source) {
        var value = Objects.requireNonNull(source, "受治理数据源不能为空");
        if (value.type() != GovernedDataSourceType.STRUCTURED_DATASET) {
            throw new DataSourceStateException("当前数据源不是 STRUCTURED_DATASET 类型");
        }
        return value;
    }

    private static SourceRevision requireCurrentRevision(
            GovernedDataSource source, SourceRevision revision) {
        var value = Objects.requireNonNull(revision, "来源修订不能为空");
        if (value.sourceType() != GovernedDataSourceType.STRUCTURED_DATASET
                || !source.tenantId().equals(value.tenantId())
                || !source.knowledgeSpaceId().equals(value.knowledgeSpaceId())
                || !source.id().equals(value.dataSourceId())
                || source.currentRevision() != value.revision()) {
            throw new DataSourceStateException("来源当前修订不允许完成结构化数据集同步");
        }
        return value;
    }

    private static void requireNormalizedContent(SourceRevision revision, TextContentReference content) {
        var value = Objects.requireNonNull(content, "同步产物内容引用不能为空");
        if (value.version() != Math.toIntExact(revision.revision())) {
            throw new DataSourceStateException("结构化数据集同步产物内容不属于当前来源修订");
        }
    }
}
