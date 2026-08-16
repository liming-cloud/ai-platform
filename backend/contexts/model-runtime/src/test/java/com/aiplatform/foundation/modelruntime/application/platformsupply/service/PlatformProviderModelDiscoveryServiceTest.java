package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformProviderModelDiscoveryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void 未持有平台模型权限时不得读取连接或写入发现事实() {
        var service = new PlatformProviderModelDiscoveryService(
                new ForbiddenConnectionRepository(),
                List.of(),
                discovery -> { throw new AssertionError("无权请求不得写入平台模型发现"); },
                CLOCK,
                () -> 620000000000000001L,
                Duration.ofHours(1));

        assertThrows(ModelCatalogPermissionDeniedException.class, () -> service.discover(
                new DiscoverPlatformProviderModelsCommand(
                        sessionWithoutPlatformModelWrite(), "610000000000000001", 1, Duration.ofSeconds(3))));
    }

    @Test
    void 草稿平台连接通过发现完成验证且不需要模型代码() {
        var connection = draftConnection();
        var repository = new SingleConnectionRepository(connection);
        var store = new CapturingStore();
        var service = new PlatformProviderModelDiscoveryService(
                repository,
                List.of(new DeterministicDiscoverer()),
                store,
                CLOCK,
                () -> 620000000000000001L,
                Duration.ofHours(1));

        var result = service.discover(new DiscoverPlatformProviderModelsCommand(
                platformModelWriter(), connection.id().toString(), connection.version(), Duration.ofSeconds(3)));

        assertEquals("620000000000000001", result.discoveryId());
        assertEquals(2, result.models().size());
        assertEquals(1L, store.discovery.connectionRevision());
        assertEquals(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.VERIFIED,
                repository.connection.status());
        assertEquals(connection.id(), store.discovery.connectionId());
    }

    private static PlatformProviderConnection verifiedConnection() {
        return draftConnection().recordSuccessfulProbe("DETERMINISTIC_OK", Instant.parse("2026-08-13T01:00:00Z"));
    }

    private static PlatformProviderConnection draftConnection() {
        return PlatformProviderConnection.create(
                new PlatformProviderConnectionId(610000000000000001L),
                ProviderKind.DETERMINISTIC,
                "平台确定性连接",
                URI.create("memory://platform-model"),
                "local",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/610000000000000001"),
                Instant.parse("2026-08-13T00:00:00Z"));
    }

    private static PlatformRequestContext sessionWithoutPlatformModelWrite() {
        return new PlatformRequestContext(
                new SubjectId(101L), CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS), "trc_platform_discovery");
    }

    private static PlatformRequestContext platformModelWriter() {
        return new PlatformRequestContext(
                new SubjectId(102L), CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.MODEL_PLATFORM_WRITE),
                "trc_platform_discovery_writer");
    }

    private static final class DeterministicDiscoverer implements PlatformProviderModelDiscoverer {
        @Override public ProviderKind providerKind() { return ProviderKind.DETERMINISTIC; }
        @Override public ProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
            return new ProviderModelDiscoveryObservation(
                    true,
                    Set.of(
                            new DiscoveredModel("deterministic-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT)),
                            new DiscoveredModel("deterministic-embedding", ModelType.EMBEDDING,
                                    Set.of(ModelCapability.EMBEDDING))),
                    "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO, CLOCK.instant());
        }
    }

    private static final class CapturingStore implements PlatformProviderModelDiscoveryStore {
        private PlatformProviderModelDiscovery discovery;
        @Override public void record(PlatformProviderModelDiscovery discovery) { this.discovery = discovery; }
    }

    private static final class SingleConnectionRepository implements PlatformProviderConnectionRepository {
        private PlatformProviderConnection connection;
        private SingleConnectionRepository(PlatformProviderConnection connection) { this.connection = connection; }
        @Override public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(
                com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) { throw new AssertionError(); }
        @Override public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) {
            return connection.id().equals(id) ? Optional.of(connection) : Optional.empty();
        }
        @Override public boolean update(PlatformProviderConnection updated, long expectedVersion) {
            if (connection.version() != expectedVersion) return false;
            connection = updated;
            return true;
        }
    }

    private static final class ForbiddenConnectionRepository implements PlatformProviderConnectionRepository {
        @Override public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(
                com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) { throw new AssertionError(); }
        @Override public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) {
            throw new AssertionError("无权请求不得读取平台连接");
        }
    }
}
