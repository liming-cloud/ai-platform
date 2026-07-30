package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.synchronization.SynchronizeStructuredDatasetCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset.StructuredDatasetCsvProjectionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.StructuredDatasetCsvSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

/** 编排结构化数据集的事务外投影、私有文本写入与领域同步结果提交。 */
public final class StructuredDatasetCsvSynchronizationService {
    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private final GovernedDataSourceRepository repository; private final StructuredDatasetCsvProjectionPort projection;
    private final TextContentStagingPort staging; private final TextContentObjectStoragePort storage;
    private final Clock clock; private final EntityIdGenerator identifiers; private final StructuredDatasetCsvSynchronization synchronization;
    public StructuredDatasetCsvSynchronizationService(GovernedDataSourceRepository repository, StructuredDatasetCsvProjectionPort projection, TextContentStagingPort staging, TextContentObjectStoragePort storage, Clock clock, EntityIdGenerator identifiers, StructuredDatasetCsvSynchronization synchronization) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空"); this.projection = Objects.requireNonNull(projection, "CSV 投影端口不能为空"); this.staging = Objects.requireNonNull(staging, "文本暂存端口不能为空"); this.storage = Objects.requireNonNull(storage, "文本对象存储端口不能为空"); this.clock = Objects.requireNonNull(clock, "时钟不能为空"); this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空"); this.synchronization = Objects.requireNonNull(synchronization, "结构化数据集同步领域服务不能为空");
    }
    public GovernedDataSourceLifecycleResult synchronize(SynchronizeStructuredDatasetCommand command) {
        Objects.requireNonNull(command, "结构化数据集同步命令不能为空");
        if (!command.context().hasPermission(WRITE)) throw new com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException();
        var space = KnowledgeSpaceId.parse(command.knowledgeSpaceId()); var sourceId = DataSourceId.parse(command.dataSourceId());
        var source = repository.findSource(command.context().tenantId(), space, sourceId).orElseThrow(DataSourceNotFoundException::new);
        if (source.version() != command.expectedVersion()) throw new DataSourceConcurrentModificationException();
        var syncing = synchronization.begin(source, command.expectedVersion()); updateOrThrow(syncing, command.expectedVersion());
        var revision = repository.findRevision(command.context().tenantId(), space, sourceId, source.currentRevision()).orElseThrow(DataSourceNotFoundException::new);
        try {
            var projected = projection.project(revision.structuredDataset());
            try (var staged = staging.stage(new ByteArrayInputStream(projected.normalizedText().getBytes(StandardCharsets.UTF_8)))) {
            var stored = storage.put(new TextContentStorageRequest(source.tenantId(), space, sourceId, Math.toIntExact(revision.revision()), staged, clock.instant()));
            requireStoredContent(revision, staged, stored);
            try { var completion = synchronization.complete(syncing, revision, new SyncArtifactId(identifiers.nextId()), stored, projected.recordCount(), projected.skippedBlankRowCount(), clock.instant()); repository.completeSynchronization(completion); return GovernedDataSourceLifecycleResult.from(completion.completedSource(), completion.artifact(), completion.qualityReport()); }
            catch (RuntimeException exception) { storage.delete(stored); throw exception; }
            }
        } catch (IOException | RuntimeException exception) { var failed = syncing.failSynchronization(syncing.version()); updateOrThrow(failed, syncing.version()); return GovernedDataSourceLifecycleResult.from(failed); }
    }
    private void updateOrThrow(com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource source, long version) { if (!repository.update(source, version)) throw new DataSourceConcurrentModificationException(); }
    private static void requireStoredContent(com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision revision, com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent staged, com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference stored) {
        if (stored == null || stored.version() != Math.toIntExact(revision.revision()) || !stored.sha256().equals(staged.sha256()) || stored.byteSize() != staged.byteSize()) throw new IllegalStateException("规范化文本对象完整性校验失败");
    }
}
