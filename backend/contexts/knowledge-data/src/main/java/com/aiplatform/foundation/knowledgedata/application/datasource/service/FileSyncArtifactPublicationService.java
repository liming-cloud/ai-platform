package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.PublishFileSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.time.Clock;
import java.util.Objects;

/**
 * 编排 FILE 来源同步产物的显式发布。
 *
 * <p>应用层只选择同一来源下的已同步产物并从仓储读取实际质量结论；仓储会在锁定来源的事务中再次复核，
 * 因此调用方无法以伪造的非阻断质量绕过发布门禁。</p>
 */
public final class FileSyncArtifactPublicationService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final GovernedDataSourceRepository repository;
    private final Clock clock;

    public FileSyncArtifactPublicationService(GovernedDataSourceRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /** 发布指定同步产物，并返回来源的新版本。 */
    public GovernedDataSourceLifecycleResult publish(PublishFileSyncArtifactCommand command) {
        Objects.requireNonNull(command, "同步产物发布命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var spaceId = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var sourceId = DataSourceId.parse(command.dataSourceId());
        var artifactId = SyncArtifactId.parse(command.artifactId());
        var source = repository.findSource(command.context().tenantId(), spaceId, sourceId)
                .orElseThrow(DataSourceNotFoundException::new);
        if (source.type() != GovernedDataSourceType.FILE) {
            throw new DataSourceNotFoundException();
        }
        if (source.version() != command.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }
        var artifact = repository.findArtifact(command.context().tenantId(), spaceId, artifactId)
                .filter(value -> value.dataSourceId().equals(source.id()))
                .orElseThrow(DataSourceNotFoundException::new);
        var report = repository.findQualityReport(command.context().tenantId(), spaceId, artifactId)
                .orElseThrow(DataSourceNotFoundException::new);
        var publication = PublishedSyncArtifact.create(artifact, report, clock.instant());
        var published = repository.publishArtifact(publication, command.expectedVersion());
        return new GovernedDataSourceLifecycleResult(
                published.id().toString(),
                published.knowledgeSpaceId().toString(),
                published.status(),
                published.version());
    }
}
