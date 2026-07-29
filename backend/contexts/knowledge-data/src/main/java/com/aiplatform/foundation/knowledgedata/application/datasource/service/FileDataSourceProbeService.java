package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.ProbeFileDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.datasource.port.out.FileSourceObjectProbe;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceOperation;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceStateException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.util.Objects;

/**
 * 编排文件对象的事务外探测与受控失败恢复。
 */
public final class FileDataSourceProbeService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final GovernedDataSourceRepository repository;
    private final FileSourceObjectProbe objectProbe;

    public FileDataSourceProbeService(
            GovernedDataSourceRepository repository,
            FileSourceObjectProbe objectProbe) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.objectProbe = Objects.requireNonNull(objectProbe, "文件对象探测器不能为空");
    }

    /**
     * 持久化开始探测后再核验对象元数据，避免外部 I/O 占用数据库事务。
     */
    public GovernedDataSourceLifecycleResult probe(ProbeFileDataSourceCommand command) {
        Objects.requireNonNull(command, "文件探测命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var spaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var sourceId = DataSourceId.parse(command.dataSourceId());
        var source = repository.findSource(command.context().tenantId(), spaceId, sourceId)
                .orElseThrow(DataSourceNotFoundException::new);
        if (source.type() != GovernedDataSourceType.FILE) {
            throw new DataSourceNotFoundException();
        }
        if (source.version() != command.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }
        var probing = startOrResumeProbe(source, command.expectedVersion());
        if (!repository.update(probing, command.expectedVersion())) {
            throw new DataSourceConcurrentModificationException();
        }
        try {
            var revision = repository.findRevision(
                    command.context().tenantId(), spaceId, sourceId, source.currentRevision())
                    .orElseThrow(DataSourceNotFoundException::new);
            objectProbe.verify(revision);
        } catch (RuntimeException exception) {
            var failed = probing.failProbe(probing.version());
            updateOrThrow(failed, probing.version());
            return result(failed);
        }
        var ready = probing.completeProbe(probing.version());
        updateOrThrow(ready, probing.version());
        return result(ready);
    }

    private void updateOrThrow(GovernedDataSource source, long expectedVersion) {
        if (!repository.update(source, expectedVersion)) {
            throw new DataSourceConcurrentModificationException();
        }
    }

    /**
     * 仅允许失败于探测的来源恢复为探测中，避免把同步失败误作为文件探测重试。
     */
    private static GovernedDataSource startOrResumeProbe(
            GovernedDataSource source, long expectedVersion) {
        if (source.status() != GovernedDataSourceStatus.FAILED) {
            return source.startProbe(expectedVersion);
        }
        if (source.failedOperation() != DataSourceOperation.PROBE) {
            throw new DataSourceStateException("当前失败操作不允许重新探测");
        }
        return source.retry(expectedVersion);
    }

    private static GovernedDataSourceLifecycleResult result(GovernedDataSource source) {
        return GovernedDataSourceLifecycleResult.from(source);
    }
}
