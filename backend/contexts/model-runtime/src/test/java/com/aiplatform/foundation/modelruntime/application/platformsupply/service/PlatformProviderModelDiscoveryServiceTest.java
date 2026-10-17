package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
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
                (connection, version, discovery) -> { throw new AssertionError("无权请求不得写入平台模型发现"); },
                CLOCK,
                () -> 620000000000000001L,
                Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        assertThrows(ModelCatalogPermissionDeniedException.class, () -> service.discover(
                new DiscoverPlatformProviderModelsCommand(
                        sessionWithoutPlatformModelWrite(), "610000000000000001", 1, Duration.ofSeconds(3))));
    }

    @Test
    void 旧修订不得执行模型发现且返回供应商连接冲突() {
        var connection = verifiedConnection();
        var service = new PlatformProviderModelDiscoveryService(
                new SingleConnectionRepository(connection),
                List.of(new ForbiddenDiscoverer()),
                (updated, version, discovery) -> {
                    throw new AssertionError("旧修订不得写入平台模型发现");
                },
                CLOCK,
                () -> 620000000000000001L,
                Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        assertThrows(ProviderConnectionConcurrentModificationException.class, () -> service.discover(
                new DiscoverPlatformProviderModelsCommand(
                        platformModelWriter(), connection.id().toString(), 0, Duration.ofSeconds(3))));
    }

    @Test
    void 草稿平台连接通过发现完成验证且不需要模型代码() {
        var connection = draftConnection();
        var repository = new SingleConnectionRepository(connection);
        var store = new CapturingStore(repository);
        var service = new PlatformProviderModelDiscoveryService(
                repository,
                List.of(new DeterministicDiscoverer()),
                store,
                CLOCK,
                () -> 620000000000000001L,
                Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        var result = service.discover(new DiscoverPlatformProviderModelsCommand(
                platformModelWriter(), connection.id().toString(), connection.version(), Duration.ofSeconds(3)));

        assertEquals("620000000000000001", result.discoveryId());
        assertEquals(2, result.models().size());
        assertEquals(1L, store.discovery.connectionRevision());
        assertEquals(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.VERIFIED,
                repository.connection.status());
        assertEquals(connection.id(), store.discovery.connectionId());
    }

    @Test
    void 方舟发现必须向适配器提供区域和独立AKSK引用() {
        var connection = PlatformProviderConnection.create(
                new PlatformProviderConnectionId(610000000000000002L),
                ProviderKind.OPENAI_COMPATIBLE,
                PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("VOLCENGINE", "2026-10-12", "CHAT", null),
                "火山方舟",
                URI.create("https://ark.cn-beijing.volces.com/api/v3"),
                "cn-beijing",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/610000000000000002"),
                Instant.parse("2026-08-13T00:00:00Z"));
        var repository = new SingleConnectionRepository(connection);
        var store = new CapturingStore(repository);
        var discoverer = new CapturingArkDiscoverer();
        var service = new PlatformProviderModelDiscoveryService(
                repository, List.of(discoverer), store, CLOCK,
                () -> 620000000000000002L, Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        service.discover(new DiscoverPlatformProviderModelsCommand(
                platformModelWriter(), connection.id().toString(), 0, Duration.ofSeconds(3)));

        assertEquals("cn-beijing", discoverer.request.region());
        var references = discoverer.request.discoveryCredentials().orElseThrow();
        assertEquals("secret://platform-provider-discovery-access-key/610000000000000002",
                references.accessKeyId());
        assertEquals("secret://platform-provider-discovery-secret-key/610000000000000002",
                references.secretAccessKey());
    }

    @Test
    void 已验证连接重新发现成功必须刷新验证时间和修订() {
        var connection = verifiedConnection();
        var repository = new SingleConnectionRepository(connection);
        var store = new CapturingStore(repository);
        var service = new PlatformProviderModelDiscoveryService(repository,
                List.of(new DeterministicDiscoverer()), store, CLOCK,
                () -> 620000000000000003L, Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        service.discover(new DiscoverPlatformProviderModelsCommand(platformModelWriter(),
                connection.id().toString(), connection.version(), Duration.ofSeconds(3)));

        assertEquals(CLOCK.instant(), repository.connection.lastProbedAt());
        assertEquals(connection.version() + 1, repository.connection.version());
        assertEquals(repository.connection.version(), store.discovery.connectionRevision());
    }

    @Test
    void 已验证连接重新发现失败必须撤销旧验证结果() {
        var connection = verifiedConnection();
        var repository = new SingleConnectionRepository(connection);
        var store = new CapturingStore(repository);
        var discoverer = new DeterministicDiscoverer() {
            @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation discover(
                    PlatformProviderModelDiscoveryRequest request) {
                return new com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation(
                        false, Set.of(), "DISCOVERY_UNAVAILABLE", Duration.ZERO, CLOCK.instant());
            }
        };
        var service = new PlatformProviderModelDiscoveryService(repository, List.of(discoverer),
                store, CLOCK, () -> 620000000000000004L, Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        service.discover(new DiscoverPlatformProviderModelsCommand(platformModelWriter(),
                connection.id().toString(), connection.version(), Duration.ofSeconds(3)));

        assertEquals(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.DRAFT,
                repository.connection.status());
        assertFalse(repository.connection.lastProbeSucceeded());
        assertEquals(CLOCK.instant(), repository.connection.lastProbedAt());
        assertEquals(connection.version() + 1, repository.connection.version());
        assertEquals(repository.connection.version(), store.discovery.connectionRevision());
        assertNull(store.discovery.expiresAt());
    }

    @Test
    void 外部发现期间停用连接必须拒绝迟到响应且不得写入候选() {
        var connection = verifiedConnection();
        var repository = new SingleConnectionRepository(connection);
        var store = new CapturingStore(repository);
        var discoverer = new DeterministicDiscoverer() {
            @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation discover(
                    PlatformProviderModelDiscoveryRequest request) {
                repository.update(connection.disable(), connection.version());
                return super.discover(request);
            }
        };
        var service = new PlatformProviderModelDiscoveryService(repository, List.of(discoverer),
                store, CLOCK, () -> 620000000000000005L, Duration.ofHours(1), new com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionWritePolicy(true));

        assertThrows(ProviderConnectionConcurrentModificationException.class, () -> service.discover(
                new DiscoverPlatformProviderModelsCommand(platformModelWriter(), connection.id().toString(),
                        connection.version(), Duration.ofSeconds(3))));

        assertEquals(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.DISABLED,
                repository.connection.status());
        assertNull(store.discovery);
    }

    private static PlatformProviderConnection verifiedConnection() {
        return draftConnection().recordSuccessfulProbe("DETERMINISTIC_OK", Instant.parse("2026-08-13T01:00:00Z"));
    }

    private static PlatformProviderConnection draftConnection() {
        return PlatformProviderConnection.create(
                new PlatformProviderConnectionId(610000000000000001L),
                ProviderKind.DETERMINISTIC,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
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

    private static class DeterministicDiscoverer implements PlatformProviderModelDiscoverer {
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKind() { return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.DETERMINISTIC; }
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile discoveryProfile() {
            return com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG;
        }
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation discover(PlatformProviderModelDiscoveryRequest request) {
            return new com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation(
                    true,
                    Set.of(
                            com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate.confirmedByProvider(
                                    "deterministic-chat", com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.CHAT, Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.CHAT)),
                            com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate.confirmedByProvider(
                                    "deterministic-embedding", com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.EMBEDDING,
                                    Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.EMBEDDING))),
                    "DETERMINISTIC_DISCOVERY_OK", Duration.ZERO, CLOCK.instant());
        }
    }

    private static final class ForbiddenDiscoverer implements PlatformProviderModelDiscoverer {
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKind() { return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.DETERMINISTIC; }
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile discoveryProfile() {
            return com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG;
        }
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation discover(
                PlatformProviderModelDiscoveryRequest request) {
            throw new AssertionError("旧修订不得调用平台模型发现适配器");
        }
    }

    private static final class CapturingArkDiscoverer implements PlatformProviderModelDiscoverer {
        private PlatformProviderModelDiscoveryRequest request;

        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKind() { return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.OPENAI_COMPATIBLE; }
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile discoveryProfile() {
            return com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE;
        }
        @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation discover(
                PlatformProviderModelDiscoveryRequest request) {
            this.request = request;
            return new com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation(
                    true,
                    Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate.requiresAdministratorConfirmation(
                            "ep-202608240001")),
                    "VOLCENGINE_ARK_DISCOVERY_OK",
                    Duration.ZERO,
                    CLOCK.instant());
        }
    }

    private static final class CapturingStore implements PlatformProviderModelDiscoveryStore {
        private final SingleConnectionRepository connections;
        private PlatformProviderModelDiscovery discovery;
        private CapturingStore(SingleConnectionRepository connections) { this.connections = connections; }
        @Override public void record(PlatformProviderConnection connection, long expectedVersion,
                PlatformProviderModelDiscovery discovery) {
            if (!connections.update(connection, expectedVersion)) {
                throw new ProviderConnectionConcurrentModificationException();
            }
            this.discovery = discovery;
        }
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
