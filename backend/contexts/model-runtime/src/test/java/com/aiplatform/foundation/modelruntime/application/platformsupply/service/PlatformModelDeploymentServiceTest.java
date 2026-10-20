package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeResult;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.publication.PlatformProviderConnectionPublication;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformModelDeploymentServiceTest {
    private static final PlatformProviderConnectionId CONNECTION_ID = PlatformProviderConnectionId.parse("610000000000000001");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void 已验证但未发布的连接不能创建平台部署() {
        assertUnpublishedConnection(fixture());
    }

    @Test
    void 当前连接修订明确发布后可以创建草稿但不自动验证或发布部署() {
        var fixture = fixture();
        when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(publication(CONNECTION_ID, 1L)));

        var result = fixture.service().create(command());

        assertEquals("DRAFT", result.deployment().status());
        assertEquals(CONNECTION_ID.toString(), result.deployment().providerConnectionId());
        assertEquals(1L, result.deployment().providerConnectionRevision());
    }

    @Test
    void 旧连接修订的发布不能提升为当前修订的发布() {
        var fixture = fixture();
        when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(publication(CONNECTION_ID, 0L)));

        assertUnpublishedConnection(fixture);
    }

    @Test
    void 其他连接的发布不能作为当前部署来源() {
        var fixture = fixture();
        var otherId = PlatformProviderConnectionId.parse("610000000000000002");
        when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(publication(otherId, 1L)));

        assertUnpublishedConnection(fixture);
    }

    @Test
    void 未发布来源的旧草稿不能发起能力探测() {
        var fixture = fixture();
        var draft = historicalDraft();
        when(fixture.deployments().findById(draft.id())).thenReturn(Optional.of(draft));

        var failure = assertThrows(IllegalStateException.class, () -> fixture.service().probe(
                new ProbePlatformModelDeploymentCommand(command().context(), draft.id().toString(),
                        draft.version(), Duration.ofSeconds(5))));

        assertEquals("平台供应商连接当前修订尚未发布", failure.getMessage());
        verifyNoInteractions(fixture.probeProvider(), fixture.verificationStore());
        verify(fixture.deployments(), never()).update(any(), anyLong());
    }

    @Test
    void 未发布来源的已验证部署不能发布() {
        var fixture = fixture();
        var verified = historicalDraft().verify(new PlatformModelCapabilityVerificationId(630000000000000001L),
                NOW.plusSeconds(3600));
        when(fixture.deployments().findById(verified.id())).thenReturn(Optional.of(verified));
        when(fixture.deployments().update(any(), anyLong())).thenReturn(true);

        var failure = assertThrows(IllegalStateException.class, () -> fixture.service().publish(
                new PublishPlatformModelDeploymentCommand(command().context(), verified.id().toString(), verified.version())));

        assertEquals("平台供应商连接当前修订尚未发布", failure.getMessage());
        verify(fixture.deployments(), never()).update(any(), anyLong());
        verifyNoInteractions(fixture.probeProvider(), fixture.verificationStore());
    }

    @Test
    void 当前来源明确发布后旧草稿仍可完成能力探测() {
        var fixture = fixture();
        var draft = historicalDraft();
        when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(publication(CONNECTION_ID, 1L)));
        when(fixture.deployments().findById(draft.id())).thenReturn(Optional.of(draft));

        var result = fixture.service().probe(new ProbePlatformModelDeploymentCommand(command().context(),
                draft.id().toString(), draft.version(), Duration.ofSeconds(5)));

        assertEquals("VERIFIED", result.deployment().status());
        verify(fixture.probeProvider()).probe(any());
        verify(fixture.verificationStore()).commit(any(), eq(draft.version()));
    }

    @Test
    void 当前来源明确发布后已验证部署仍可发布() {
        var fixture = fixture();
        var verified = historicalDraft().verify(new PlatformModelCapabilityVerificationId(630000000000000001L),
                NOW.plusSeconds(3600));
        when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(publication(CONNECTION_ID, 1L)));
        when(fixture.deployments().findById(verified.id())).thenReturn(Optional.of(verified));
        when(fixture.deployments().update(any(), anyLong())).thenReturn(true);

        var result = fixture.service().publish(new PublishPlatformModelDeploymentCommand(command().context(),
                verified.id().toString(), verified.version()));

        assertEquals("PUBLISHED", result.status());
        assertEquals(verified.version() + 1, result.version());
        verify(fixture.deployments()).update(any(), eq(verified.version()));
        verifyNoInteractions(fixture.probeProvider(), fixture.verificationStore());
    }

    @Test
    void 探测不接受其他连接或旧修订的发布事实() {
        var otherId = PlatformProviderConnectionId.parse("610000000000000002");
        for (var invalid : java.util.List.of(publication(CONNECTION_ID, 0L), publication(otherId, 1L))) {
            var fixture = fixture();
            var draft = historicalDraft();
            when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(invalid));
            when(fixture.deployments().findById(draft.id())).thenReturn(Optional.of(draft));

            var failure = assertThrows(IllegalStateException.class, () -> fixture.service().probe(
                    new ProbePlatformModelDeploymentCommand(command().context(), draft.id().toString(),
                            draft.version(), Duration.ofSeconds(5))));

            assertEquals("平台供应商连接当前修订尚未发布", failure.getMessage());
            verifyNoInteractions(fixture.probeProvider(), fixture.verificationStore());
            verify(fixture.deployments(), never()).update(any(), anyLong());
        }
    }

    @Test
    void 发布不接受其他连接或旧修订的发布事实() {
        var otherId = PlatformProviderConnectionId.parse("610000000000000002");
        for (var invalid : java.util.List.of(publication(CONNECTION_ID, 0L), publication(otherId, 1L))) {
            var fixture = fixture();
            var verified = historicalDraft().verify(new PlatformModelCapabilityVerificationId(630000000000000001L),
                    NOW.plusSeconds(3600));
            when(fixture.publications().find(CONNECTION_ID, 1L)).thenReturn(Optional.of(invalid));
            when(fixture.deployments().findById(verified.id())).thenReturn(Optional.of(verified));

            var failure = assertThrows(IllegalStateException.class, () -> fixture.service().publish(
                    new PublishPlatformModelDeploymentCommand(command().context(), verified.id().toString(), verified.version())));

            assertEquals("平台供应商连接当前修订尚未发布", failure.getMessage());
            verify(fixture.deployments(), never()).update(any(), anyLong());
            verifyNoInteractions(fixture.probeProvider(), fixture.verificationStore());
        }
    }

    private static PlatformModelDeployment historicalDraft() {
        return PlatformModelDeployment.create(new PlatformModelDeploymentId(620000000000000001L), CONNECTION_ID,
                1L, "deterministic-embedding", "平台嵌入部署", ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING), 8192, null, 1024, NOW.minusSeconds(120));
    }

    private static void assertUnpublishedConnection(DeploymentFixture fixture) {
        var failure = assertThrows(IllegalStateException.class, () -> fixture.service().create(command()));
        assertEquals("平台供应商连接当前修订尚未发布", failure.getMessage());
        verifyNoInteractions(fixture.discoveries(), fixture.deployments());
    }

    private static PlatformProviderConnectionPublication publication(PlatformProviderConnectionId id, long connectionVersion) {
        return new PlatformProviderConnectionPublication(610000000000000003L, id, 1L, connectionVersion,
                101L, NOW.minusSeconds(60), NOW.minusSeconds(30));
    }

    private static CreatePlatformModelDeploymentCommand command() {
        var context = new PlatformRequestContext(new SubjectId(101L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.MODEL_PLATFORM_WRITE),
                "trc_platform_deployment_source");
        return new CreatePlatformModelDeploymentCommand(context, "platform-deployment-source", CONNECTION_ID.toString(), 1L,
                "deterministic-embedding", "平台嵌入部署", ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING), 8192, null, 1024);
    }

    private static DeploymentFixture fixture() {
        var deployments = mock(PlatformModelDeploymentRepository.class);
        var connections = mock(PlatformProviderConnectionRepository.class);
        var discoveries = mock(PlatformProviderModelDiscoveryLookup.class);
        var publications = mock(PlatformProviderConnectionPublicationRepository.class);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var connection = mock(PlatformProviderConnection.class);
        when(connection.id()).thenReturn(CONNECTION_ID);
        when(connection.version()).thenReturn(1L);
        when(connection.status()).thenReturn(ProviderConnectionStatus.VERIFIED);
        when(connection.providerKind()).thenReturn(ProviderKind.DETERMINISTIC);
        when(connection.endpoint()).thenReturn(URI.create("https://models.example.invalid/v1"));
        when(connection.credentialReference()).thenReturn(mock(CredentialReference.class));
        when(connections.findById(CONNECTION_ID)).thenReturn(Optional.of(connection));
        var candidate = mock(PlatformDiscoveredModelCandidate.class);
        when(candidate.capabilities()).thenReturn(Set.of(ModelCapability.EMBEDDING));
        when(discoveries.findCurrentCandidate(any(), anyLong(), any(), any(), any()))
                .thenReturn(Optional.of(candidate));
        when(deployments.create(any())).thenAnswer(invocation -> {
            var creation = invocation.getArgument(0, PlatformModelDeploymentCreation.class);
            return new PlatformModelDeploymentCreationOutcome(creation.deployment(), false);
        });
        var probeProvider = mock(PlatformModelProbeProviderService.class);
        when(probeProvider.probe(any())).thenReturn(new ProviderProbeResult(ProviderKind.DETERMINISTIC, true,
                Set.of(ModelCapability.EMBEDDING), Duration.ZERO, "PLATFORM_MODEL_PROBE_OK", NOW));
        var verificationStore = mock(PlatformModelDeploymentVerificationStore.class);
        var service = new PlatformModelDeploymentService(deployments, connections, publications, discoveries,
                probeProvider, verificationStore, clock, () -> 630000000000000002L, Duration.ofHours(24));
        return new DeploymentFixture(service, publications, discoveries, deployments, probeProvider, verificationStore);
    }

    private record DeploymentFixture(PlatformModelDeploymentService service,
            PlatformProviderConnectionPublicationRepository publications,
            PlatformProviderModelDiscoveryLookup discoveries, PlatformModelDeploymentRepository deployments,
            PlatformModelProbeProviderService probeProvider, PlatformModelDeploymentVerificationStore verificationStore) {}

    @Test
    void 无平台模型权限时创建探测发布均不得读取来源或写入部署() {
        var publications = mock(PlatformProviderConnectionPublicationRepository.class);
        var service = new PlatformModelDeploymentService(
                new ForbiddenDeploymentRepository(), new ForbiddenConnectionRepository(), publications, new ForbiddenDiscoveryLookup(),
                new PlatformModelProbeProviderService(java.util.List.of(), Clock.systemUTC()),
                new ForbiddenVerificationStore(), Clock.systemUTC(), () -> 620000000000000001L, Duration.ofHours(24));

        assertThrows(ModelCatalogPermissionDeniedException.class, () -> service.create(new CreatePlatformModelDeploymentCommand(
                sessionWithoutPlatformModelWrite(), "platform-deploy-0001", "610000000000000001", 1,
                "deterministic-embedding", "平台嵌入", ModelType.EMBEDDING, Set.of(ModelCapability.EMBEDDING),
                8192, null, 1024)));
        assertThrows(ModelCatalogPermissionDeniedException.class, () -> service.probe(new ProbePlatformModelDeploymentCommand(
                sessionWithoutPlatformModelWrite(), "620000000000000001", 0L, Duration.ofSeconds(5))));
        assertThrows(ModelCatalogPermissionDeniedException.class, () -> service.publish(new PublishPlatformModelDeploymentCommand(
                sessionWithoutPlatformModelWrite(), "620000000000000001", 1L)));
        verifyNoInteractions(publications);
    }

    private static PlatformRequestContext sessionWithoutPlatformModelWrite() {
        return new PlatformRequestContext(new SubjectId(101L), CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS), "trc_platform_deployment");
    }

    private static final class ForbiddenDeploymentRepository implements PlatformModelDeploymentRepository {
        @Override public PlatformModelDeploymentCreationOutcome create(PlatformModelDeploymentCreation creation) { throw new AssertionError("无权请求不得写入平台部署"); }
        @Override public Optional<PlatformModelDeployment> findById(PlatformModelDeploymentId id) { throw new AssertionError("无权请求不得读取平台部署"); }
        @Override public PlatformModelDeploymentPage findPage(long pageNumber, long pageSize) { throw new AssertionError("无权请求不得读取平台部署"); }
        @Override public boolean update(PlatformModelDeployment deployment, long expectedVersion) { throw new AssertionError("无权请求不得更新平台部署"); }
    }
    private static final class ForbiddenConnectionRepository implements PlatformProviderConnectionRepository {
        @Override public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) { throw new AssertionError("无权请求不得写入平台连接"); }
        @Override public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) { throw new AssertionError("无权请求不得读取平台连接"); }
    }
    private static final class ForbiddenDiscoveryLookup implements PlatformProviderModelDiscoveryLookup {
        @Override public Optional<com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate> findCurrentCandidate(PlatformProviderConnectionId connectionId, long connectionRevision, String modelCode, ModelType modelType, java.time.Instant now) { throw new AssertionError("无权请求不得读取平台候选"); }
        @Override public com.aiplatform.foundation.shared.contract.paging.PageResult<com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate> findCurrentCandidates(PlatformProviderConnectionId connectionId, long connectionRevision, com.aiplatform.foundation.shared.contract.paging.PageQuery page, java.time.Instant now, String q) { throw new AssertionError("无权请求不得读取平台候选"); }
    }
    private static final class ForbiddenVerificationStore implements PlatformModelDeploymentVerificationStore {
        @Override public void commit(PlatformModelDeploymentVerificationResult verification, long expectedDeploymentVersion) { throw new AssertionError("无权请求不得写入平台验证事实"); }
    }
}
