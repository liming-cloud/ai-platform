package com.aiplatform.foundation.knowledgedata.application.datasource.service.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.knowledgedata.application.datasource.command.publication.PublishObjectStorageSyncArtifactCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.exception.KnowledgePermissionDeniedException;
import com.aiplatform.foundation.knowledgedata.domain.common.model.CreatorId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceType;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifact;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.SyncArtifactId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.quality.QualityReport;
import com.aiplatform.foundation.knowledgedata.domain.datasource.repository.GovernedDataSourceRepository;
import com.aiplatform.foundation.knowledgedata.domain.datasource.service.ObjectStorageSynchronization.ArtifactPublishing;
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

class ObjectStorageSyncArtifactPublicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final KnowledgeSpaceId SPACE = new KnowledgeSpaceId(210000000000000001L);
    private static final DataSourceId SOURCE_ID = new DataSourceId(230000000000000001L);
    private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");

    @Test
    void 知识管理员只能发布同一对象存储来源的通过质量产物() {
        var repository = mock(GovernedDataSourceRepository.class);
        var source = readySource();
        var artifact = artifact(source);
        when(repository.findSource(TENANT, SPACE, SOURCE_ID)).thenReturn(Optional.of(source));
        when(repository.findArtifact(TENANT, SPACE, artifact.id())).thenReturn(Optional.of(artifact));
        when(repository.findQualityReport(TENANT, SPACE, artifact.id()))
                .thenReturn(Optional.of(QualityReport.passed(artifact, "object-storage-text-v1")));

        var result = service(repository).publish(command(true, source.version()));

        assertEquals(GovernedDataSourceStatus.READY, result.status());
        assertEquals(source.version() + 1, result.version());
        verify(repository).publishArtifact(any());
    }

    @Test
    void 无知识写权限时发布前拒绝且不读取任何事实() {
        var repository = mock(GovernedDataSourceRepository.class);

        assertThrows(KnowledgePermissionDeniedException.class,
                () -> service(repository).publish(command(false, 4)));

        verifyNoInteractions(repository);
    }

    private static ObjectStorageSyncArtifactPublicationService service(GovernedDataSourceRepository repository) {
        return new ObjectStorageSyncArtifactPublicationService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ArtifactPublishing());
    }

    private static PublishObjectStorageSyncArtifactCommand command(boolean writable, long expectedVersion) {
        return new PublishObjectStorageSyncArtifactCommand(
                context(writable), SPACE.toString(), SOURCE_ID.toString(), "240000000000000001", expectedVersion);
    }

    private static GovernedDataSource readySource() {
        return GovernedDataSource.create(
                        TENANT,
                        SPACE,
                        SOURCE_ID,
                        GovernedDataSourceType.OBJECT_STORAGE,
                        "平台对象来源",
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
                source.currentRevision(),
                new TextContentReference(
                        1,
                        "foundation-v1/tenants/1/knowledge-data/artifacts/object-storage.txt",
                        "a".repeat(64),
                        1,
                        TextContentReference.UTF8_TEXT,
                        NOW),
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
                "trc-object-storage-publication");
    }
}
