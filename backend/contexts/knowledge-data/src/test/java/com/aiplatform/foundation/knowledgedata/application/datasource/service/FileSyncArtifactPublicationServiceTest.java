package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.PublishFileSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.datasource.exception.DataSourceNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.FileSyncArtifactPublishing;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FileSyncArtifactPublicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-29T06:00:00Z");

    @Test
    void 知识管理员只能发布同一文件来源的非阻断同步产物() {
        var repository = mock(GovernedDataSourceRepository.class);
        var source = readySource();
        var artifact = artifact(source);
        var published = source.publishArtifact(source.version());
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findArtifact(TENANT, SPACE, artifact.id())).thenReturn(Optional.of(artifact));
        when(repository.findQualityReport(TENANT, SPACE, artifact.id()))
                .thenReturn(Optional.of(QualityReport.warned(artifact, "file-quality-v1", 1)));
        var result = service(repository).publish(command(true, SOURCE_ID, artifact.id(), source.version()));

        assertEquals(GovernedDataSourceStatus.READY, result.status());
        assertEquals(published.version(), result.version());
        verify(repository).publishArtifact(org.mockito.ArgumentMatchers.argThat(value ->
                value.publication().artifact().equals(artifact)
                        && value.publication().qualityStatus().name().equals("WARN")
                        && value.publication().ruleSetVersion().equals("file-quality-v1")
                        && value.publication().publishedAt().equals(NOW)
                        && value.expectedSourceVersion() == source.version()));
    }

    @Test
    void 无知识写权限时发布前拒绝且不读取任何事实() {
        var repository = mock(GovernedDataSourceRepository.class);

        assertThrows(KnowledgePermissionDeniedException.class,
                () -> service(repository).publish(command(false, SOURCE_ID, new SyncArtifactId(1L), 2)));

        verifyNoInteractions(repository);
    }

    @Test
    void 不属于目标来源的产物按不存在处理且不尝试发布() {
        var repository = mock(GovernedDataSourceRepository.class);
        var source = readySource();
        var another = readySource(new DataSourceId(230000000000000002L));
        var artifact = artifact(another);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findArtifact(TENANT, SPACE, artifact.id())).thenReturn(Optional.of(artifact));

        assertThrows(DataSourceNotFoundException.class,
                () -> service(repository).publish(command(true, SOURCE_ID, artifact.id(), source.version())));

        verify(repository).findSource(TENANT, SPACE, SOURCE_ID);
        verify(repository).findArtifact(TENANT, SPACE, artifact.id());
        verify(repository, org.mockito.Mockito.never()).findQualityReport(any(), any(), any());
        verify(repository, org.mockito.Mockito.never()).publishArtifact(any());
    }

    private static FileSyncArtifactPublicationService service(GovernedDataSourceRepository repository) {
        return new FileSyncArtifactPublicationService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), new FileSyncArtifactPublishing());
    }

    private static PublishFileSyncArtifactCommand command(
            boolean writable, DataSourceId sourceId, SyncArtifactId artifactId, long expectedVersion) {
        return new PublishFileSyncArtifactCommand(
                context(writable), SPACE.toString(), sourceId.toString(), artifactId.toString(), expectedVersion);
    }

    private static GovernedDataSource readySource() {
        return readySource(SOURCE_ID);
    }

    private static GovernedDataSource readySource(DataSourceId sourceId) {
        return GovernedDataSource.create(
                        TENANT,
                        SPACE,
                        sourceId,
                        GovernedDataSourceType.FILE,
                        "受控文件",
                        new CreatorId(4921825928971026434L),
                        NOW)
                .startProbe(0)
                .completeProbe(1)
                .startSynchronization(2)
                .completeSynchronization(3);
    }

    private static SyncArtifact artifact(GovernedDataSource source) {
        return SyncArtifact.create(
                new SyncArtifactId(240000000000000001L),
                TENANT,
                SPACE,
                source.id(),
                1,
                "a".repeat(64),
                1,
                NOW);
    }

    private static TrustedRequestContext context(boolean writable) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                writable ? Set.of(new PermissionCode("knowledge:write")) : Set.of(),
                "trc-file-publication");
    }
}
