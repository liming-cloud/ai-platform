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
import java.time.Instant;
import java.util.Objects;

/**
 * 收敛 OBJECT_STORAGE 同步状态、冻结集合与私有规范化文本产物的不变量。
 *
 * <p>调用方只能提交事务外已核验的私有文本引用；本服务不读取平台对象、不鉴权也不依赖持久化技术。</p>
 */
public final class ObjectStorageSynchronization {

    private static final String QUALITY_RULE_SET_VERSION = "object-storage-text-v1";

    /**
     * 开始或仅按同步失败语义恢复对象存储同步。
     *
     * @throws DataSourceStateException 来源类型、状态或修订不允许同步时抛出。
     */
    public GovernedDataSource begin(GovernedDataSource source, long expectedVersion) {
        var objectStorage = requireObjectStorage(source);
        if (objectStorage.status() != GovernedDataSourceStatus.FAILED) {
            return objectStorage.startSynchronization(expectedVersion);
        }
        if (objectStorage.failedOperation() != DataSourceOperation.SYNCHRONIZATION) {
            throw new DataSourceStateException("当前失败操作不允许重新同步");
        }
        return objectStorage.retry(expectedVersion);
    }

    /**
     * 以当前冻结对象集合完成同步，并创建唯一可发布的通过质量结论。
     *
     * @throws DataSourceStateException 来源、修订、状态或产物内容版本不满足不变量时抛出。
     */
    public ObjectStorageSynchronizationCompletion complete(
            GovernedDataSource source,
            SourceRevision revision,
            SyncArtifactId artifactId,
            TextContentReference content,
            Instant synchronizedAt) {
        var syncingSource = requireObjectStorage(source);
        var currentRevision = requireCurrentRevision(syncingSource, revision);
        var value = Objects.requireNonNull(content, "同步产物内容引用不能为空");
        if (value.version() != Math.toIntExact(currentRevision.revision())) {
            throw new DataSourceStateException("对象存储同步产物内容不属于当前来源修订");
        }
        var artifact = SyncArtifact.create(
                Objects.requireNonNull(artifactId, "同步产物标识不能为空"),
                syncingSource.tenantId(),
                syncingSource.knowledgeSpaceId(),
                syncingSource.id(),
                currentRevision.revision(),
                value,
                currentRevision.objectStorageObjects().objects().size(),
                Objects.requireNonNull(synchronizedAt, "同步时间不能为空"));
        var completedSource = syncingSource.completeSynchronization(syncingSource.version());
        return new ObjectStorageSynchronizationCompletion(
                syncingSource.version(),
                completedSource,
                artifact,
                QualityReport.passed(artifact, QUALITY_RULE_SET_VERSION));
    }

    private static GovernedDataSource requireObjectStorage(GovernedDataSource source) {
        var value = Objects.requireNonNull(source, "受治理数据源不能为空");
        if (value.type() != GovernedDataSourceType.OBJECT_STORAGE) {
            throw new DataSourceStateException("当前数据源不是 OBJECT_STORAGE 类型");
        }
        return value;
    }

    private static SourceRevision requireCurrentRevision(GovernedDataSource source, SourceRevision revision) {
        var value = Objects.requireNonNull(revision, "来源修订不能为空");
        if (value.sourceType() != GovernedDataSourceType.OBJECT_STORAGE
                || !source.tenantId().equals(value.tenantId())
                || !source.knowledgeSpaceId().equals(value.knowledgeSpaceId())
                || !source.id().equals(value.dataSourceId())
                || source.currentRevision() != value.revision()) {
            throw new DataSourceStateException("来源当前修订不允许完成对象存储同步");
        }
        return value;
    }
}
