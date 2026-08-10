package com.aiplatform.foundation.modelruntime.application.provider.service;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.application.provider.command.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.command.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.command.RotateProviderConnectionCredentialCommand;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderConnectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T04:30:00Z");
    private static final long CONNECTION_ID = 310000000000000001L;
    private static final String CONNECTION_ID_TEXT = Long.toString(CONNECTION_ID);

    @Test
    void 创建结果不得回显密钥引用() {
        var repository = new InMemoryRepository();
        var service = service(repository, successfulProbe());

        var result = service.create(createCommand(context("model:write")));

        assertFalse(result.replayed());
        assertTrue(result.providerConnection().credentialConfigured());
        assertEquals(CONNECTION_ID_TEXT, result.providerConnection().id());
        assertEquals(ProviderConnectionStatus.DRAFT.name(), result.providerConnection().status());
        assertFalse(result.providerConnection().toString().contains("secret://demo/openai"));
        assertFalse(result.providerConnection().toString().contains("memory://deterministic"));
    }

    @Test
    void 缺少模型写权限不得创建或探测() {
        var repository = new InMemoryRepository();
        var service = service(repository, successfulProbe());

        assertThrows(
                ModelCatalogPermissionDeniedException.class,
                () -> service.create(createCommand(context("model:read"))));
        assertTrue(repository.findById(
                        new TenantId(100000000000000001L),
                        new ProviderConnectionId(CONNECTION_ID))
                .isEmpty());
    }

    @Test
    void 探测成功应持久化已验证状态和显式能力() {
        var repository = new InMemoryRepository();
        var service = service(repository, successfulProbe());
        service.create(createCommand(context("model:write")));

        var result = service.probe(new ProbeProviderConnectionCommand(
                context("model:write"),
                CONNECTION_ID_TEXT,
                0,
                "demo-chat",
                Duration.ofSeconds(2)));

        assertTrue(result.reachable());
        assertEquals(
                Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT),
                result.capabilities());
        assertEquals(ProviderConnectionStatus.VERIFIED.name(), result.providerConnection().status());
        assertEquals(1, result.providerConnection().version());
    }

    @Test
    void 探测失败应保存失败事实且连接保持草稿() {
        var repository = new InMemoryRepository();
        var service = service(repository, failedProbe());
        service.create(createCommand(context("model:write")));

        var result = service.probe(new ProbeProviderConnectionCommand(
                context("model:write"),
                CONNECTION_ID_TEXT,
                0,
                "demo-chat",
                Duration.ofSeconds(2)));

        assertFalse(result.reachable());
        assertEquals(ProviderConnectionStatus.DRAFT.name(), result.providerConnection().status());
        assertEquals(false, result.providerConnection().lastProbeSucceeded());
        assertEquals("DETERMINISTIC_FAILED", result.detailCode());
    }

    @Test
    void 轮换凭据应原子更新私有引用并使探测事实失效() {
        var repository = new InMemoryRepository();
        var writer = new TrackingCredentialWriter();
        var service = service(repository, successfulProbe(), writer);
        service.create(createCommand(context("model:write")));
        service.probe(new ProbeProviderConnectionCommand(
                context("model:write"), CONNECTION_ID_TEXT, 0, "demo-chat", Duration.ofSeconds(2)));

        var result = service.rotateCredential(new RotateProviderConnectionCredentialCommand(
                context("model:write"),
                CONNECTION_ID_TEXT,
                1,
                SecretValue.copyOf("rotated-api-key".toCharArray())));

        assertEquals(2, result.providerConnection().version());
        assertEquals(ProviderConnectionStatus.DRAFT.name(), result.providerConnection().status());
        assertEquals(null, result.providerConnection().lastProbeSucceeded());
        assertEquals(2, writer.references.size());
        assertEquals(
                "secret://provider-connection/310000000000000001",
                writer.references.get(1).value());
    }

    private static ProviderConnectionService service(
            InMemoryRepository repository,
            ProviderConnectionProbe probe) {
        return service(repository, probe, new TrackingCredentialWriter());
    }

    private static ProviderConnectionService service(
            InMemoryRepository repository,
            ProviderConnectionProbe probe,
            ProviderConnectionCredentialWriter credentials) {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new ProviderConnectionService(
                repository,
                new ProbeProviderService(List.of(probe), clock),
                credentials,
                new PassThroughTransaction(),
                clock,
                () -> CONNECTION_ID,
                3);
    }

    private static ProviderConnectionProbe successfulProbe() {
        return probe(new ProviderProbeObservation(
                true,
                Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT),
                Duration.ofMillis(10),
                "DETERMINISTIC_OK"));
    }

    private static ProviderConnectionProbe failedProbe() {
        return probe(new ProviderProbeObservation(
                false,
                Set.of(),
                Duration.ofMillis(10),
                "DETERMINISTIC_FAILED"));
    }

    private static ProviderConnectionProbe probe(ProviderProbeObservation observation) {
        return new ProviderConnectionProbe() {
            @Override
            public ProviderKind providerKind() {
                return ProviderKind.DETERMINISTIC;
            }

            @Override
            public ProviderProbeObservation probe(
                    com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest request) {
                return observation;
            }
        };
    }

    private static CreateProviderConnectionCommand createCommand(
            TrustedRequestContext context) {
        return new CreateProviderConnectionCommand(
                context,
                "provider-request-0001",
                ProviderKind.DETERMINISTIC,
                "演示模型连接",
                URI.create("memory://deterministic"),
                "本地",
                AuthenticationType.API_KEY,
                SecretValue.copyOf("initial-api-key".toCharArray()));
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-model-catalog");
    }

    private static final class InMemoryRepository implements ProviderConnectionRepository {

        private ProviderConnection connection;

        @Override
        public ProviderConnectionCreationOutcome create(
                ProviderConnectionCreation creation,
                int tenantLimit) {
            connection = creation.connection();
            return new ProviderConnectionCreationOutcome(connection, false);
        }

        @Override
        public Optional<ProviderConnection> findById(
                TenantId tenantId,
                ProviderConnectionId connectionId) {
            if (connection == null
                    || !connection.tenantId().equals(tenantId)
                    || !connection.id().equals(connectionId)) {
                return Optional.empty();
            }
            return Optional.of(connection);
        }

        @Override
        public ProviderConnectionPage findPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            var items = connection == null ? List.<ProviderConnection>of() : List.of(connection);
            return new ProviderConnectionPage(
                    items,
                    items.size());
        }

        @Override
        public boolean update(ProviderConnection updated, long expectedVersion) {
            if (connection == null || connection.version() != expectedVersion) {
                return false;
            }
            connection = updated;
            return true;
        }
    }

    private static final class TrackingCredentialWriter
            implements ProviderConnectionCredentialWriter {

        private final List<com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference>
                references = new java.util.ArrayList<>();

        @Override
        public void write(
                TenantId tenantId,
                com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference reference,
                SecretValue apiKey) {
            references.add(reference);
        }
    }

    private static final class PassThroughTransaction
            implements ProviderConnectionCredentialTransaction {

        @Override
        public <T> T execute(java.util.function.Supplier<T> action) {
            return action.get();
        }
    }
}
