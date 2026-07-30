package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.SynchronizeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.TextContentIntegrityException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileDataSourceSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 编排 FILE 来源的事务外内容核验与领域同步操作提交。
 *
 * <p>对象流读取必须在来源已持久化为 `SYNCING` 后发生。读取或完整性核验失败只登记
 * `FAILED(SYNCHRONIZATION)`，不保存半成品；产物和质量结论由领域服务创建，再由仓储在同一事务中提交。</p>
 */
public final class FileDataSourceSynchronizationService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private static final int BUFFER_SIZE = 8192;

    private final GovernedDataSourceRepository repository;
    private final TextContentObjectStoragePort storage;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final FileDataSourceSynchronization synchronization;

    public FileDataSourceSynchronizationService(
            GovernedDataSourceRepository repository,
            TextContentObjectStoragePort storage,
            Clock clock,
            EntityIdGenerator identifiers,
            FileDataSourceSynchronization synchronization) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.storage = Objects.requireNonNull(storage, "文本对象存储端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.synchronization = Objects.requireNonNull(synchronization, "文件同步领域服务不能为空");
    }

    /**
     * 先持久化同步中状态，再读取冻结对象并登记原子同步结果。
     */
    public GovernedDataSourceLifecycleResult synchronize(SynchronizeFileDataSourceCommand command) {
        Objects.requireNonNull(command, "文件同步命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var spaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var sourceId = DataSourceId.parse(command.dataSourceId());
        var source = repository.findSource(command.context().tenantId(), spaceId, sourceId)
                .orElseThrow(DataSourceNotFoundException::new);
        if (source.version() != command.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }
        var syncing = synchronization.begin(source, command.expectedVersion());
        updateOrThrow(syncing, command.expectedVersion());

        var revision = repository.findRevision(
                        command.context().tenantId(), spaceId, sourceId, source.currentRevision())
                .orElseThrow(DataSourceNotFoundException::new);
        FileContentSnapshot content;
        try {
            content = synchronize(revision);
        } catch (IOException | RuntimeException exception) {
            var failed = syncing.failSynchronization(syncing.version());
            updateOrThrow(failed, syncing.version());
            return result(failed);
        }

        var completion = synchronization.complete(
                syncing,
                revision,
                new SyncArtifactId(identifiers.nextId()),
                content.content(),
                content.recordCount(),
                clock.instant());
        repository.completeSynchronization(completion);
        return GovernedDataSourceLifecycleResult.from(
                completion.completedSource(), completion.artifact(), completion.qualityReport());
    }

    private FileContentSnapshot synchronize(SourceRevision revision) throws IOException {
        var file = Objects.requireNonNull(revision.fileObject(), "文件来源修订必须包含对象事实");
        // 文件修订已限定唯一 UTF-8 语义；对象存储端口仍使用既有文本引用的协议格式。
        var reference = new TextContentReference(
                Math.toIntExact(revision.revision()),
                file.objectKey(),
                file.contentDigest(),
                file.byteSize(),
                TextContentReference.UTF8_TEXT,
                revision.createdAt());
        var digest = sha256();
        long byteSize = 0;
        try (var input = storage.read(reference)) {
            Objects.requireNonNull(input, "对象内容流不能为空");
            var buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                byteSize += read;
            }
        }
        var contentDigest = HexFormat.of().formatHex(digest.digest());
        if (byteSize != file.byteSize() || !contentDigest.equals(file.contentDigest())) {
            throw new TextContentIntegrityException();
        }
        return new FileContentSnapshot(
                new TextContentReference(
                        Math.toIntExact(revision.revision()),
                        file.objectKey(),
                        contentDigest,
                        byteSize,
                        TextContentReference.UTF8_TEXT,
                        revision.createdAt()),
                1);
    }

    private void updateOrThrow(GovernedDataSource source, long expectedVersion) {
        if (!repository.update(source, expectedVersion)) {
            throw new DataSourceConcurrentModificationException();
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static GovernedDataSourceLifecycleResult result(GovernedDataSource source) {
        return GovernedDataSourceLifecycleResult.from(source);
    }

    private record FileContentSnapshot(TextContentReference content, long recordCount) {}
}
