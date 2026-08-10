package com.aiplatform.foundation.modelruntime.application.provider.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryCandidateReader;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryCandidatePage;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderModelDiscoveryCandidateReadServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION =
            new ProviderConnectionId(310000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T06:00:00Z");

    @Test
    void 只向有模型读取权限的当前已验证连接返回未过期发现候选() {
        var connections = mock(ProviderConnectionRepository.class);
        var candidates = mock(ProviderModelDiscoveryCandidateReader.class);
        var connection = mock(ProviderConnection.class);
        when(connection.status()).thenReturn(ProviderConnectionStatus.VERIFIED);
        when(connection.version()).thenReturn(4L);
        when(connections.findById(TENANT, CONNECTION)).thenReturn(Optional.of(connection));
        when(candidates.findCurrentCandidates(TENANT, CONNECTION, 4L, 2, 20, NOW)).thenReturn(
                new ProviderModelDiscoveryCandidatePage(java.util.List.of(
                new DiscoveredModel("deterministic-chat", ModelType.CHAT,
                        Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT))), 21));
        var service = new ProviderModelDiscoveryCandidateReadService(
                connections, candidates, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.listCurrentCandidates(
                context("model:read"), CONNECTION.toString(), new PageQuery(2, 20));

        assertEquals(21, result.totalElements());
        assertEquals("deterministic-chat", result.items().getFirst().modelCode());
        assertEquals(ModelType.CHAT, result.items().getFirst().modelType());
        verify(candidates).findCurrentCandidates(TENANT, CONNECTION, 4L, 2, 20, NOW);
    }

    @Test
    void 无模型读取权限时不得读取连接或候选模型() {
        var connections = mock(ProviderConnectionRepository.class);
        var candidates = mock(ProviderModelDiscoveryCandidateReader.class);
        var service = new ProviderModelDiscoveryCandidateReadService(
                connections, candidates, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(
                com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException.class,
                () -> service.listCurrentCandidates(
                        context("model:write"), CONNECTION.toString(), new PageQuery(1, 20)));

        verifyNoInteractions(connections, candidates);
    }

    @Test
    void 未验证连接不得读取旧修订候选模型() {
        var connections = mock(ProviderConnectionRepository.class);
        var candidates = mock(ProviderModelDiscoveryCandidateReader.class);
        var connection = mock(ProviderConnection.class);
        when(connection.status()).thenReturn(ProviderConnectionStatus.DRAFT);
        when(connections.findById(TENANT, CONNECTION)).thenReturn(Optional.of(connection));
        var service = new ProviderModelDiscoveryCandidateReadService(
                connections, candidates, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class,
                () -> service.listCurrentCandidates(
                        context("model:read"), CONNECTION.toString(), new PageQuery(1, 20)));

        verifyNoInteractions(candidates);
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trc_model_discovery_candidate_read");
    }
}
