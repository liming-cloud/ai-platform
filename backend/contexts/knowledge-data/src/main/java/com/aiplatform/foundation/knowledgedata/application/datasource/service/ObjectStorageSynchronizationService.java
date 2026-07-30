package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.synchronization.SynchronizeObjectStorageDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.PlatformObjectContentReaderPort;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.StagedTextContent;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentObjectStoragePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStagingPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.content.TextContentStorageRequest;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.ObjectStorageSynchronization;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/** 编排 OBJECT_STORAGE 冻结集合的事务外读取、私有文本写入与领域同步结果提交。 */
public final class ObjectStorageSynchronizationService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");
    private static final byte[] SEPARATOR = "\n\n".getBytes(StandardCharsets.UTF_8);

    private final GovernedDataSourceRepository repository;
    private final PlatformObjectContentReaderPort reader;
    private final TextContentStagingPort staging;
    private final TextContentObjectStoragePort storage;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final ObjectStorageSynchronization synchronization;

    public ObjectStorageSynchronizationService(
            GovernedDataSourceRepository repository,
            PlatformObjectContentReaderPort reader,
            TextContentStagingPort staging,
            TextContentObjectStoragePort storage,
            Clock clock,
            EntityIdGenerator identifiers,
            ObjectStorageSynchronization synchronization) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.reader = Objects.requireNonNull(reader, "平台对象正文读取端口不能为空");
        this.staging = Objects.requireNonNull(staging, "文本暂存端口不能为空");
        this.storage = Objects.requireNonNull(storage, "文本对象存储端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.synchronization = Objects.requireNonNull(synchronization, "对象存储同步领域服务不能为空");
    }

    /** 同步已冻结的对象集合；不重新解析范围或公开内部对象位置。 */
    public GovernedDataSourceLifecycleResult synchronize(SynchronizeObjectStorageDataSourceCommand command) {
        Objects.requireNonNull(command, "对象存储同步命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var space = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var sourceId = DataSourceId.parse(command.dataSourceId());
        var source = repository.findSource(command.context().tenantId(), space, sourceId)
                .orElseThrow(DataSourceNotFoundException::new);
        if (source.version() != command.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }
        var syncing = synchronization.begin(source, command.expectedVersion());
        updateOrThrow(syncing, command.expectedVersion());
        var revision = repository.findRevision(command.context().tenantId(), space, sourceId, source.currentRevision())
                .orElseThrow(DataSourceNotFoundException::new);
        try {
            return synchronizeFrozenCollection(syncing, revision, space, sourceId);
        } catch (IOException | RuntimeException exception) {
            var failed = syncing.failSynchronization(syncing.version());
            updateOrThrow(failed, syncing.version());
            return GovernedDataSourceLifecycleResult.from(failed);
        }
    }

    private GovernedDataSourceLifecycleResult synchronizeFrozenCollection(
            GovernedDataSource syncing, SourceRevision revision, KnowledgeSpaceId space, DataSourceId sourceId) throws IOException {
        try (var input = concatenatedContent(revision); var staged = staging.stage(input)) {
            var stored = storage.put(new TextContentStorageRequest(
                    syncing.tenantId(), space, sourceId, Math.toIntExact(revision.revision()), staged, clock.instant()));
            requireStoredContent(revision, staged, stored);
            try {
                var completion = synchronization.complete(
                        syncing, revision, new SyncArtifactId(identifiers.nextId()), stored, clock.instant());
                repository.completeSynchronization(completion);
                return GovernedDataSourceLifecycleResult.from(
                        completion.completedSource(), completion.artifact(), completion.qualityReport());
            } catch (RuntimeException exception) {
                storage.delete(stored);
                throw exception;
            }
        }
    }

    private InputStream concatenatedContent(SourceRevision revision) {
        var streams = new ArrayList<InputStream>();
        var objects = revision.objectStorageObjects().objects();
        for (var index = 0; index < objects.size(); index++) {
            if (index > 0) {
                streams.add(new ByteArrayInputStream(SEPARATOR));
            }
            streams.add(reader.open(revision, objects.get(index)));
        }
        return new SequenceInputStream(Collections.enumeration(streams));
    }

    private void updateOrThrow(GovernedDataSource source, long expectedVersion) {
        if (!repository.update(source, expectedVersion)) {
            throw new DataSourceConcurrentModificationException();
        }
    }

    private static void requireStoredContent(
            SourceRevision revision, StagedTextContent staged, TextContentReference stored) {
        if (stored == null
                || stored.version() != Math.toIntExact(revision.revision())
                || !stored.sha256().equals(staged.sha256())
                || stored.byteSize() != staged.byteSize()) {
            throw new IllegalStateException("规范化文本对象完整性校验失败");
        }
    }
}
