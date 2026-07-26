package com.aiplatform.foundation.modelruntime.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionSlice;
import com.aiplatform.foundation.modelruntime.application.probe.DefaultProbeProviderService;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionStatus;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultProviderConnectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T04:30:00Z");
    private static final UUID CONNECTION_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000001");

    @Test
    void 创建结果不得回显密钥引用() {
        var repository = new InMemoryRepository();
        var service = service(repository, successfulProbe());

        var result = service.create(createCommand(context("model:write")));

        assertFalse(result.replayed());
        assertTrue(result.providerConnection().credentialConfigured());
        assertEquals(CONNECTION_ID, result.providerConnection().id());
        assertEquals(ProviderConnectionStatus.DRAFT.name(), result.providerConnection().status());
        assertFalse(result.providerConnection().toString().contains("secret://demo/openai"));
    }

    @Test
    void 缺少模型写权限不得创建或探测() {
        var repository = new InMemoryRepository();
        var service = service(repository, successfulProbe());

        assertThrows(
                ModelCatalogPermissionDeniedException.class,
                () -> service.create(createCommand(context("model:read"))));
        assertTrue(repository.findById(
                        TenantId.parse("10000000-0000-0000-0000-000000000001"),
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
                CONNECTION_ID,
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
                CONNECTION_ID,
                0,
                "demo-chat",
                Duration.ofSeconds(2)));

        assertFalse(result.reachable());
        assertEquals(ProviderConnectionStatus.DRAFT.name(), result.providerConnection().status());
        assertEquals(false, result.providerConnection().lastProbeSucceeded());
        assertEquals("DETERMINISTIC_FAILED", result.detailCode());
    }

    private static DefaultProviderConnectionService service(
            InMemoryRepository repository,
            ProviderConnectionProbe probe) {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new DefaultProviderConnectionService(
                repository,
                new DefaultProbeProviderService(List.of(probe), clock),
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
                    com.aiplatform.foundation.modelruntime.api.model.ProviderProbeRequest request) {
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
                "secret://demo/openai");
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TenantId.parse("10000000-0000-0000-0000-000000000001"),
                new SubjectId(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                new MembershipId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
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
        public ProviderConnectionSlice findPage(
                TenantId tenantId,
                String cursor,
                int limit) {
            return new ProviderConnectionSlice(
                    connection == null ? List.of() : List.of(connection),
                    null);
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
}
