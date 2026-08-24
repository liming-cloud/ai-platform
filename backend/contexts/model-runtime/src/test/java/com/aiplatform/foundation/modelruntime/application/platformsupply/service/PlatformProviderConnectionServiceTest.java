package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformProviderConnectionServiceTest {

    @Test
    void 方舟连接创建必须在同一事务写入推理APIKey和发现AKSK() {
        var repository = new CreatingRepository();
        var writer = new CapturingCredentialWriter();
        var service = new PlatformProviderConnectionService(
                repository,
                writer,
                java.util.function.Supplier::get,
                new PlatformProbeProviderService(java.util.List.of(), Clock.systemUTC()),
                Clock.fixed(Instant.parse("2026-08-24T09:00:00Z"), java.time.ZoneOffset.UTC),
                () -> 320000000000000001L);
        try (var apiKey = SecretValue.copyOf("inference-api-key".toCharArray());
                var accessKeyId = SecretValue.copyOf("AK_TEST".toCharArray());
                var secretAccessKey = SecretValue.copyOf("SK_TEST".toCharArray())) {
            service.create(new CreatePlatformProviderConnectionCommand(
                    platformModelWriter(),
                    "platform-ark-request-0001",
                    ProviderKind.OPENAI_COMPATIBLE,
                    PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE,
                    "火山方舟",
                    URI.create("https://ark.cn-beijing.volces.com/api/v3"),
                    "cn-beijing",
                    AuthenticationType.API_KEY,
                    apiKey,
                    accessKeyId,
                    secretAccessKey));
        }

        assertEquals(List.of(
                "secret://platform-provider-connection/320000000000000001",
                "secret://platform-provider-discovery-access-key/320000000000000001",
                "secret://platform-provider-discovery-secret-key/320000000000000001"), writer.references);
    }

    @Test
    void 没有模型平台权限的会话不得建立平台连接() {
        var service = new PlatformProviderConnectionService(
                new ForbiddenPathRepository(),
                new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get,
                new PlatformProbeProviderService(java.util.List.of(), Clock.systemUTC()),
                Clock.systemUTC(),
                () -> 310000000000000001L);
        try (var apiKey = SecretValue.copyOf("test-api-key".toCharArray())) {
            assertThrows(
                    ModelCatalogPermissionDeniedException.class,
                    () -> service.create(new CreatePlatformProviderConnectionCommand(
                            sessionWithoutPlatformModelWrite(),
                            "platform-connection-0001",
                            ProviderKind.OPENAI_COMPATIBLE,
                            "平台连接",
                            URI.create("https://models.example.com/v1"),
                            "本地研发",
                            AuthenticationType.API_KEY,
                            apiKey)));
        }
    }

    @Test
    void 没有模型平台权限的会话不得探测平台连接() {
        var service = new PlatformProviderConnectionService(
                new ForbiddenPathRepository(),
                new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get,
                new PlatformProbeProviderService(java.util.List.of(), Clock.systemUTC()),
                Clock.systemUTC(),
                () -> 310000000000000001L);
        assertThrows(
                ModelCatalogPermissionDeniedException.class,
                () -> service.probe(new ProbePlatformProviderConnectionCommand(
                        sessionWithoutPlatformModelWrite(),
                        "610000000000000001",
                        0,
                        "deterministic-embedding",
                        java.time.Duration.ofSeconds(3))));
    }

    @Test
    void 成功探测只推进平台连接而不需要租户标识() {
        var connection = PlatformProviderConnection.create(
                new PlatformProviderConnectionId(610000000000000001L), ProviderKind.DETERMINISTIC,
                "平台确定性连接", URI.create("memory://platform-model"), "local", AuthenticationType.API_KEY,
                new com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference(
                        "secret://platform-provider-connection/610000000000000001"), Instant.parse("2026-08-13T00:00:00Z"));
        var repository = new SingleConnectionRepository(connection);
        var probe = new PlatformProviderConnectionProbe() {
            @Override public ProviderKind providerKind() { return ProviderKind.DETERMINISTIC; }
            @Override public com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation probe(PlatformProviderProbeRequest request) {
                return new com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation(true, Set.of(), java.time.Duration.ZERO, "DETERMINISTIC_OK");
            }
        };
        var service = new PlatformProviderConnectionService(repository, new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get, new PlatformProbeProviderService(java.util.List.of(probe), Clock.systemUTC()),
                Clock.systemUTC(), () -> 610000000000000002L);
        var result = service.probe(new ProbePlatformProviderConnectionCommand(platformModelWriter(),
                connection.id().toString(), connection.version(), "deterministic-embedding", java.time.Duration.ofSeconds(3)));
        assertEquals("VERIFIED", result.status());
        assertEquals(1L, result.version());
        assertEquals("VERIFIED", repository.connection.status().name());
    }

    private static PlatformRequestContext sessionWithoutPlatformModelWrite() {
        return new PlatformRequestContext(
                new SubjectId(101L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(PlatformPermissions.SESSION_ACCESS),
                "trc_platform_supply");
    }

    private static PlatformRequestContext platformModelWriter() {
        return new PlatformRequestContext(new SubjectId(102L), CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.MODEL_PLATFORM_WRITE), "trc_platform_supply_writer");
    }

    private static final class SingleConnectionRepository implements PlatformProviderConnectionRepository {
        private PlatformProviderConnection connection;
        private SingleConnectionRepository(PlatformProviderConnection connection) { this.connection = connection; }
        @Override public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(
                com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) { throw new AssertionError(); }
        @Override public Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) { return connection.id().equals(id) ? Optional.of(connection) : Optional.empty(); }
        @Override public boolean update(PlatformProviderConnection next, long expectedVersion) {
            if (connection.version() != expectedVersion) return false;
            connection = next;
            return true;
        }
    }

    private static final class ForbiddenPathRepository
            implements PlatformProviderConnectionRepository {
        @Override
        public com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome create(
                com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation creation) {
            throw new AssertionError("无权请求不得触及平台连接仓储");
        }
    }

    private static final class ForbiddenPathCredentialWriter
            implements PlatformProviderConnectionCredentialWriter {
        @Override
        public void write(
                com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference reference,
                SecretValue apiKey) {
            throw new AssertionError("无权请求不得写入平台凭据");
        }
    }

    private static final class CreatingRepository implements PlatformProviderConnectionRepository {
        @Override
        public PlatformProviderConnectionCreationOutcome create(PlatformProviderConnectionCreation creation) {
            return new PlatformProviderConnectionCreationOutcome(creation.connection(), false);
        }
    }

    private static final class CapturingCredentialWriter implements PlatformProviderConnectionCredentialWriter {
        private final List<String> references = new ArrayList<>();

        @Override
        public void write(
                com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference reference,
                SecretValue apiKey) {
            references.add(reference.value());
        }
    }
}
