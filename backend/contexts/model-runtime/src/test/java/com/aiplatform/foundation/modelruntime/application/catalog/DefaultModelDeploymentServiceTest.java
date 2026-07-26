package com.aiplatform.foundation.modelruntime.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.api.model.ProviderProbeResult;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentSlice;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionSlice;
import com.aiplatform.foundation.modelruntime.domain.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretReference;
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

class DefaultModelDeploymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
    private static final TenantId TENANT =
            TenantId.parse("10000000-0000-0000-0000-000000000001");
    private static final ProviderConnectionId CONNECTION_ID = new ProviderConnectionId(
            UUID.fromString("41000000-0000-0000-0000-000000000001"));
    private static final UUID DEPLOYMENT_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000001");

    @Test
    void 只能从当前已验证供应商修订创建草稿部署() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, successfulProbe());

        var created = service.create(createCommand(context("model:write")));

        assertFalse(created.replayed());
        assertEquals(ModelDeploymentStatus.DRAFT.name(), created.modelDeployment().status());
        assertEquals(1, created.modelDeployment().providerConnectionRevision());
        assertEquals(Set.of(ModelCapability.CHAT), created.modelDeployment().capabilities());
    }

    @Test
    void 探测能力覆盖声明后才可验证并发布() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, successfulProbe());
        service.create(createCommand(context("model:write")));

        var probed = service.probe(new ProbeModelDeploymentCommand(
                context("model:write"),
                DEPLOYMENT_ID,
                0,
                Duration.ofSeconds(2)));
        var published = service.publish(new PublishModelDeploymentCommand(
                context("model:write"),
                DEPLOYMENT_ID,
                1));

        assertTrue(probed.reachable());
        assertEquals(ModelDeploymentStatus.VERIFIED.name(), probed.modelDeployment().status());
        assertEquals(ModelDeploymentStatus.PUBLISHED.name(), published.status());
        assertEquals(2, published.version());
    }

    @Test
    void 能力不匹配时保持草稿且不写入新版本() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, probe(Set.of(ModelCapability.EMBEDDING)));
        service.create(createCommand(context("model:write")));

        var result = service.probe(new ProbeModelDeploymentCommand(
                context("model:write"),
                DEPLOYMENT_ID,
                0,
                Duration.ofSeconds(2)));

        assertFalse(result.reachable());
        assertEquals("MODEL_CAPABILITY_MISMATCH", result.detailCode());
        assertEquals(0, deployments.deployment.version());
    }

    @Test
    void 供应商修订漂移后不得探测或发布() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, successfulProbe());
        service.create(createCommand(context("model:write")));
        providers.connection = providers.connection.recordSuccessfulProbe(
                "DETERMINISTIC_OK",
                NOW.plusSeconds(1));

        assertThrows(
                ModelDeploymentProviderUnavailableException.class,
                () -> service.probe(new ProbeModelDeploymentCommand(
                        context("model:write"),
                        DEPLOYMENT_ID,
                        0,
                        Duration.ofSeconds(2))));
    }

    @Test
    void 缺少模型写权限不得创建部署() {
        var deployments = new InMemoryDeploymentRepository();
        var service = service(
                deployments,
                new InMemoryProviderRepository(verifiedProvider()),
                successfulProbe());

        assertThrows(
                ModelCatalogPermissionDeniedException.class,
                () -> service.create(createCommand(context("model:read"))));
        assertTrue(deployments.findById(TENANT, new ModelDeploymentId(DEPLOYMENT_ID)).isEmpty());
    }

    private static DefaultModelDeploymentService service(
            InMemoryDeploymentRepository deployments,
            InMemoryProviderRepository providers,
            ProviderProbeResult probeResult) {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new DefaultModelDeploymentService(
                deployments,
                providers,
                request -> probeResult,
                clock,
                () -> DEPLOYMENT_ID,
                6);
    }

    private static ProviderProbeResult successfulProbe() {
        return probe(Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT));
    }

    private static ProviderProbeResult probe(Set<ModelCapability> capabilities) {
        return new ProviderProbeResult(
                ProviderKind.DETERMINISTIC,
                true,
                capabilities,
                Duration.ofMillis(10),
                "DETERMINISTIC_OK",
                NOW);
    }

    private static CreateModelDeploymentCommand createCommand(
            TrustedRequestContext context) {
        return new CreateModelDeploymentCommand(
                context,
                "deployment-request-0001",
                CONNECTION_ID.value(),
                "deterministic-chat",
                "演示对话模型",
                ModelType.CHAT,
                Set.of(ModelCapability.CHAT),
                8192,
                1024,
                null);
    }

    private static ProviderConnection verifiedProvider() {
        return ProviderConnection.create(
                        TENANT,
                        CONNECTION_ID,
                        ProviderKind.DETERMINISTIC,
                        "演示供应商",
                        URI.create("memory://deterministic"),
                        "本地",
                        AuthenticationType.API_KEY,
                        new SecretReference("secret://demo/provider"),
                        NOW.minusSeconds(60))
                .recordSuccessfulProbe("DETERMINISTIC_OK", NOW.minusSeconds(30));
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                new MembershipId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-model-deployment");
    }

    private static final class InMemoryDeploymentRepository
            implements ModelDeploymentRepository {

        private ModelDeployment deployment;

        @Override
        public ModelDeploymentCreationOutcome create(
                ModelDeploymentCreation creation,
                int tenantLimit) {
            deployment = creation.deployment();
            return new ModelDeploymentCreationOutcome(deployment, false);
        }

        @Override
        public Optional<ModelDeployment> findById(
                TenantId tenantId,
                ModelDeploymentId deploymentId) {
            if (deployment == null
                    || !deployment.tenantId().equals(tenantId)
                    || !deployment.id().equals(deploymentId)) {
                return Optional.empty();
            }
            return Optional.of(deployment);
        }

        @Override
        public ModelDeploymentSlice findPage(
                TenantId tenantId,
                String cursor,
                int limit) {
            return new ModelDeploymentSlice(
                    deployment == null ? List.of() : List.of(deployment),
                    null);
        }

        @Override
        public boolean update(ModelDeployment updated, long expectedVersion) {
            if (deployment == null || deployment.version() != expectedVersion) {
                return false;
            }
            deployment = updated;
            return true;
        }
    }

    private static final class InMemoryProviderRepository
            implements ProviderConnectionRepository {

        private ProviderConnection connection;

        private InMemoryProviderRepository(ProviderConnection connection) {
            this.connection = connection;
        }

        @Override
        public ProviderConnectionCreationOutcome create(
                ProviderConnectionCreation creation,
                int tenantLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProviderConnection> findById(
                TenantId tenantId,
                ProviderConnectionId connectionId) {
            if (!connection.tenantId().equals(tenantId)
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
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(ProviderConnection updated, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }
}
