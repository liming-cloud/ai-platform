package com.aiplatform.foundation.modelruntime.application.provider.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.provider.command.DiscoverProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderModelDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");
    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION = new ProviderConnectionId(310000000000000001L);

    @Test
    void 已验证连接的成功发现应写入不可变事实且不泄漏连接细节() {
        var stored = new java.util.ArrayList<ProviderModelDiscovery>();
        var service = service(verifiedConnection(), successDiscoverer(), stored::add);

        var result = service.discover(new DiscoverProviderModelsCommand(
                context("model:write"), CONNECTION.toString(), 1, Duration.ofSeconds(2)));

        assertEquals("MODEL_DISCOVERY_OK", result.resultCode());
        assertEquals(1, stored.size());
        assertEquals("chat-model", stored.getFirst().models().iterator().next().modelCode());
        assertEquals(NOW.plus(Duration.ofHours(24)), stored.getFirst().expiresAt());
    }

    @Test
    void 连接修订漂移或未验证时不得调用发现端口() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var discoverer = discoverer(calls, observation());
        var service = service(verifiedConnection(), discoverer, ignored -> { });

        assertThrows(
                ProviderConnectionConcurrentModificationException.class,
                () -> service.discover(new DiscoverProviderModelsCommand(
                        context("model:write"), CONNECTION.toString(), 0, Duration.ofSeconds(2))));
        assertEquals(0, calls.get());
    }

    private static ProviderModelDiscoveryService service(
            ProviderConnection connection,
            ProviderModelDiscoverer discoverer,
            ProviderModelDiscoveryStore store) {
        return new ProviderModelDiscoveryService(
                new SingleConnectionRepository(connection),
                List.of(discoverer),
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 340000000000000001L,
                Duration.ofHours(24));
    }

    private static ProviderModelDiscoverer successDiscoverer() {
        return discoverer(new java.util.concurrent.atomic.AtomicInteger(), observation());
    }

    private static ProviderModelDiscoverer discoverer(
            java.util.concurrent.atomic.AtomicInteger calls,
            ProviderModelDiscoveryObservation observation) {
        return new ProviderModelDiscoverer() {
            @Override public ProviderKind providerKind() { return ProviderKind.DETERMINISTIC; }
            @Override public ProviderModelDiscoveryObservation discover(
                    com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryRequest request) {
                calls.incrementAndGet();
                return observation;
            }
        };
    }

    private static ProviderModelDiscoveryObservation observation() {
        return new ProviderModelDiscoveryObservation(true, Set.of(new DiscoveredModel(
                "chat-model", ModelType.CHAT, Set.of(ModelCapability.CHAT))),
                "MODEL_DISCOVERY_OK", Duration.ofMillis(10), NOW);
    }

    private static ProviderConnection verifiedConnection() {
        return ProviderConnection.create(TENANT, CONNECTION, ProviderKind.DETERMINISTIC,
                "演示连接", URI.create("memory://deterministic"), "本地", AuthenticationType.API_KEY,
                new com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference("secret://demo/provider"), NOW)
                .recordSuccessfulProbe("DETERMINISTIC_OK", NOW);
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(TENANT, new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE,
                1, Set.of(new PermissionCode(permission)), "trc_model_discovery");
    }

    private record SingleConnectionRepository(ProviderConnection connection) implements ProviderConnectionRepository {
        @Override public com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreationOutcome create(com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreation creation, int limit) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProviderConnection> findById(TenantId tenantId, ProviderConnectionId id) { return connection.tenantId().equals(tenantId) && connection.id().equals(id) ? Optional.of(connection) : Optional.empty(); }
        @Override public com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionPage findPage(TenantId tenantId, long pageNumber, long pageSize) { return new com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionPage(List.of(), 0); }
        @Override public boolean update(ProviderConnection updated, long expectedVersion) { return false; }
    }
}
