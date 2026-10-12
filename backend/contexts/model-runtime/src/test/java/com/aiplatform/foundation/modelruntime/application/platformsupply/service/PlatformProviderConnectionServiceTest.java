package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts;
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
    void 自定义连接指向私网时创建即失败且不保存凭据() {
        var writer = new CapturingCredentialWriter();
        var service = new PlatformProviderConnectionService(new CreatingRepository(), writer,
                java.util.function.Supplier::get, new PlatformProbeProviderService(List.of(), Clock.systemUTC()),
                Clock.systemUTC(), () -> 320000000000000001L, new com.aiplatform.foundation.security.egress.PublicHttpsEndpointPolicy(), facts(0));
        try (var key = SecretValue.copyOf("synthetic-private-key".toCharArray())) {
            assertThrows(com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException.class,
                    () -> service.create(new CreatePlatformProviderConnectionCommand(platformModelWriter(),
                            "private-endpoint-denied-0001", ProviderKind.OPENAI_COMPATIBLE,
                            PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                            new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection(
                                    "CUSTOM", "2026-10-12", "CHAT", null),
                            "禁止私网", URI.create("https://127.0.0.1/v1"), "unspecified", AuthenticationType.API_KEY, key)));
        }
        assertEquals(List.of(), writer.references);
    }

    @Test
    void 非方舟发现不得携带单个AK或SK() {
        try (var key = SecretValue.copyOf("synthetic-inference-key".toCharArray());
                var discoveryKey = SecretValue.copyOf("synthetic-discovery-key".toCharArray())) {
            for (boolean accessKeyOnly : List.of(true, false)) {
                assertThrows(IllegalArgumentException.class,
                        () -> new CreatePlatformProviderConnectionCommand(platformModelWriter(),
                                "partial-discovery-denied-0001", ProviderKind.OPENAI_COMPATIBLE,
                                PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection(
                                        "CUSTOM", "2026-10-12", "CHAT", null),
                                "不应接收控制面凭据", URI.create("https://models.example.invalid/v1"), "unspecified",
                                AuthenticationType.API_KEY, key, accessKeyOnly ? discoveryKey : null,
                                accessKeyOnly ? null : discoveryKey));
            }
        }
    }

    @Test
    void 无平台模型权限时详情查询不能读取仓储或解析对象标识() {
        var repository = org.mockito.Mockito.mock(PlatformProviderConnectionRepository.class);
        var writer = org.mockito.Mockito.mock(PlatformProviderConnectionCredentialWriter.class);
        var transactions = org.mockito.Mockito.mock(PlatformProviderConnectionCredentialTransaction.class);
        var probes = org.mockito.Mockito.mock(PlatformProbeProviderService.class);
        var identifiers = org.mockito.Mockito.mock(com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator.class);
        var facts = org.mockito.Mockito.mock(PlatformProviderConnectionFacts.class);
        var service = new PlatformProviderConnectionService(repository, writer, transactions, probes,
                Clock.systemUTC(), identifiers, endpoint -> {}, facts);
        assertThrows(ModelCatalogPermissionDeniedException.class,
                () -> service.get(sessionWithoutPlatformModelWrite(), "invalid-id"));
        org.mockito.Mockito.verifyNoInteractions(repository, writer, transactions, probes, identifiers, facts);
    }

    @Test
    void 详情事实缺失或版本与对象不匹配时失败关闭() {
        var id = new PlatformProviderConnectionId(610000000000000001L);
        var connection = org.mockito.Mockito.mock(PlatformProviderConnection.class);
        org.mockito.Mockito.when(connection.id()).thenReturn(id);
        org.mockito.Mockito.when(connection.version()).thenReturn(0L);
        var repository = org.mockito.Mockito.mock(PlatformProviderConnectionRepository.class);
        org.mockito.Mockito.when(repository.findById(id)).thenReturn(Optional.of(connection));
        var invalid = List.<java.util.Map<Long, PlatformProviderConnectionFacts.Fact>>of(
                java.util.Map.of(),
                java.util.Map.of(id.value(), new PlatformProviderConnectionFacts.Fact(id.value(), 1, true, true, true)),
                java.util.Map.of(id.value(), new PlatformProviderConnectionFacts.Fact(id.value() + 1, 0, true, true, true)));
        for (var projection : invalid) {
            var service = new PlatformProviderConnectionService(repository, new ForbiddenPathCredentialWriter(),
                    java.util.function.Supplier::get, new PlatformProbeProviderService(List.of(), Clock.systemUTC()),
                    Clock.systemUTC(), () -> { throw new AssertionError("只读查询不能生成标识"); }, endpoint -> {}, ids -> projection);
            assertThrows(com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException.class,
                    () -> service.get(platformModelWriter(), id.toString()));
        }
    }

    @Test
    void 详情不存在时返回既有安全错误且不创建连接() {
        var repository = org.mockito.Mockito.mock(PlatformProviderConnectionRepository.class);
        var id = new PlatformProviderConnectionId(610000000000000001L);
        org.mockito.Mockito.when(repository.findById(id)).thenReturn(Optional.empty());
        var service = new PlatformProviderConnectionService(repository, new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get, new PlatformProbeProviderService(List.of(), Clock.systemUTC()),
                Clock.systemUTC(), () -> { throw new AssertionError("详情查询不能生成标识"); }, endpoint -> {}, facts(0));
        assertThrows(com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException.class,
                () -> service.get(platformModelWriter(), id.toString()));
        org.mockito.Mockito.verify(repository).findById(id);
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void 非法详情标识不能触及仓储() {
        var repository = org.mockito.Mockito.mock(PlatformProviderConnectionRepository.class);
        var service = new PlatformProviderConnectionService(repository, new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get, new PlatformProbeProviderService(List.of(), Clock.systemUTC()),
                Clock.systemUTC(), () -> { throw new AssertionError("详情查询不能生成标识"); }, endpoint -> {}, facts(0));
        assertThrows(IllegalArgumentException.class, () -> service.get(platformModelWriter(), "other-tenant/1"));
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

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
                () -> 320000000000000001L, endpoint -> {}, facts(0));
        try (var apiKey = SecretValue.copyOf("inference-api-key".toCharArray());
                var accessKeyId = SecretValue.copyOf("AK_TEST".toCharArray());
                var secretAccessKey = SecretValue.copyOf("SK_TEST".toCharArray())) {
            service.create(new CreatePlatformProviderConnectionCommand(
                    platformModelWriter(),
                    "platform-ark-request-0001",
                    ProviderKind.OPENAI_COMPATIBLE,
                    PlatformModelDiscoveryProfile.VOLCENGINE_ARK_CONTROL_PLANE,
                    new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("VOLCENGINE", "2026-10-12", "CHAT", null),
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
                () -> 310000000000000001L, endpoint -> {}, facts(0));
        try (var apiKey = SecretValue.copyOf("test-api-key".toCharArray())) {
            assertThrows(
                    ModelCatalogPermissionDeniedException.class,
                    () -> service.create(new CreatePlatformProviderConnectionCommand(
                            sessionWithoutPlatformModelWrite(),
                            "platform-connection-0001",
                            ProviderKind.OPENAI_COMPATIBLE,
                            com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                            new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
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
                () -> 310000000000000001L, endpoint -> {}, facts(0));
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
                new PlatformProviderConnectionId(610000000000000001L),
                ProviderKind.DETERMINISTIC,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.DETERMINISTIC_CATALOG,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "平台确定性连接",
                URI.create("memory://platform-model"),
                "local",
                AuthenticationType.API_KEY,
                new com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference(
                        "secret://platform-provider-connection/610000000000000001"),
                Instant.parse("2026-08-13T00:00:00Z"));
        var repository = new SingleConnectionRepository(connection);
        var probe = new PlatformProviderConnectionProbe() {
            @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind providerKind() { return com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind.DETERMINISTIC; }
            @Override public com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation probe(PlatformProviderProbeRequest request) {
                return new com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation(true, Set.of(), java.time.Duration.ZERO, "DETERMINISTIC_OK");
            }
        };
        var service = new PlatformProviderConnectionService(repository, new ForbiddenPathCredentialWriter(),
                java.util.function.Supplier::get, new PlatformProbeProviderService(java.util.List.of(probe), Clock.systemUTC()),
                Clock.systemUTC(), () -> 610000000000000002L, endpoint -> {}, facts(1));
        var result = service.probe(new ProbePlatformProviderConnectionCommand(platformModelWriter(),
                connection.id().toString(), connection.version(), "deterministic-embedding", java.time.Duration.ofSeconds(3)));
        assertEquals("VERIFIED", result.status());
        assertEquals(1L, result.version());
        assertEquals("VERIFIED", repository.connection.status().name());
        // 持有私有引用不等于数据库已有凭据；不得固定回显“已配置”。
        org.junit.jupiter.api.Assertions.assertFalse(result.credentialConfigured());
    }

    private static com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts facts(long version) {
        return ids -> ids.stream().collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(),
                id -> new com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionFacts.Fact(
                        id, version, false, false, false)));
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
