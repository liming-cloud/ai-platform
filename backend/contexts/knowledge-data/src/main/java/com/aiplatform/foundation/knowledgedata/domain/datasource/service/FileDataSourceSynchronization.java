package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import java.time.Instant;
import java.util.Objects;

/**
 * 收敛 FILE 来源同步状态、不可变产物与质量结论的跨聚合不变量。
 *
 * <p>调用方必须先在事务外完成对象读取和完整性核验，再将不可变摘要事实交给本服务；本服务不读取对象、
 * 不执行鉴权，也不依赖持久化技术。</p>
 */
public final class FileDataSourceSynchronization {

    private static final String QUALITY_RULE_SET_VERSION = "file-integrity-v1";

    /**
     * 开始或仅按同步失败语义恢复 FILE 来源的同步。
     *
     * @throws DataSourceStateException 来源不是 FILE、状态不允许同步或修订过期时拒绝操作。
     */
    public GovernedDataSource begin(GovernedDataSource source, long expectedVersion) {
        var fileSource = requireFileSource(source);
        if (fileSource.status() != GovernedDataSourceStatus.FAILED) {
            return fileSource.startSynchronization(expectedVersion);
        }
        if (fileSource.failedOperation() != DataSourceOperation.SYNCHRONIZATION) {
            throw new DataSourceStateException("当前失败操作不允许重新同步");
        }
        return fileSource.retry(expectedVersion);
    }

    /**
     * 以当前 FILE 来源修订完成同步，并创建不可变产物和通过质量结论。
     *
     * @throws DataSourceStateException 来源、修订或状态不满足同步完成不变量时拒绝操作。
     */
    public FileSynchronizationCompletion complete(
            GovernedDataSource source,
            SourceRevision revision,
            SyncArtifactId artifactId,
            String contentDigest,
            long recordCount,
            Instant synchronizedAt) {
        var syncingSource = requireFileSource(source);
        var currentRevision = requireCurrentRevision(syncingSource, revision);
        var artifact = SyncArtifact.create(
                Objects.requireNonNull(artifactId, "同步产物标识不能为空"),
                syncingSource.tenantId(),
                syncingSource.knowledgeSpaceId(),
                syncingSource.id(),
                currentRevision.revision(),
                contentDigest,
                recordCount,
                Objects.requireNonNull(synchronizedAt, "同步时间不能为空"));
        var report = QualityReport.passed(artifact, QUALITY_RULE_SET_VERSION);
        var completedSource = syncingSource.completeSynchronization(syncingSource.version());
        return new FileSynchronizationCompletion(syncingSource.version(), completedSource, artifact, report);
    }

    private static GovernedDataSource requireFileSource(GovernedDataSource source) {
        var value = Objects.requireNonNull(source, "受治理数据源不能为空");
        if (value.type() != GovernedDataSourceType.FILE) {
            throw new DataSourceStateException("当前数据源不是 FILE 类型");
        }
        return value;
    }

    private static SourceRevision requireCurrentRevision(GovernedDataSource source, SourceRevision revision) {
        var value = Objects.requireNonNull(revision, "来源修订不能为空");
        if (value.sourceType() != GovernedDataSourceType.FILE
                || !source.tenantId().equals(value.tenantId())
                || !source.knowledgeSpaceId().equals(value.knowledgeSpaceId())
                || !source.id().equals(value.dataSourceId())
                || source.currentRevision() != value.revision()) {
            throw new DataSourceStateException("来源当前修订不允许完成文件同步");
        }
        return value;
    }
}
