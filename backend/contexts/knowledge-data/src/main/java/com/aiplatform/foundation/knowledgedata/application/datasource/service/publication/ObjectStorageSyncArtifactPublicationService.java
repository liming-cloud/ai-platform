package com.aiplatform.foundation.knowledgedata.application.datasource.service.publication;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.publication.PublishObjectStorageSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.dto.GovernedDataSourceLifecycleResult;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.ObjectStorageSynchronization.ArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.time.Clock;
import java.util.Objects;

/** 编排 OBJECT_STORAGE 来源同步产物的显式发布。 */
public final class ObjectStorageSyncArtifactPublicationService {

    private static final PermissionCode WRITE = new PermissionCode("knowledge:write");

    private final GovernedDataSourceRepository repository;
    private final Clock clock;
    private final ArtifactPublishing publishing;

    public ObjectStorageSyncArtifactPublicationService(
            GovernedDataSourceRepository repository,
            Clock clock,
            ArtifactPublishing publishing) {
        this.repository = Objects.requireNonNull(repository, "受治理数据源仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.publishing = Objects.requireNonNull(publishing, "对象存储产物发布领域服务不能为空");
    }

    /** 发布指定同步产物，并返回来源的新版本。 */
    public GovernedDataSourceLifecycleResult publish(PublishObjectStorageSyncArtifactCommand command) {
        Objects.requireNonNull(command, "同步产物发布命令不能为空");
        if (!command.context().hasPermission(WRITE)) {
            throw new KnowledgePermissionDeniedException();
        }
        var space = KnowledgeSpaceId.parse(command.knowledgeSpaceId());
        var sourceId = DataSourceId.parse(command.dataSourceId());
        var artifactId = SyncArtifactId.parse(command.artifactId());
        var source = repository.findSource(command.context().tenantId(), space, sourceId)
                .orElseThrow(DataSourceNotFoundException::new);
        if (source.version() != command.expectedVersion()) {
            throw new DataSourceConcurrentModificationException();
        }
        var artifact = repository.findArtifact(command.context().tenantId(), space, artifactId)
                .filter(value -> value.dataSourceId().equals(source.id()))
                .orElseThrow(DataSourceNotFoundException::new);
        var report = repository.findQualityReport(command.context().tenantId(), space, artifactId)
                .orElseThrow(DataSourceNotFoundException::new);
        var publication = publishing.publish(source, artifact, report, clock.instant());
        repository.publishArtifact(publication);
        return GovernedDataSourceLifecycleResult.from(
                publication.publishedSource(), publication.publication().artifact(), report);
    }
}
