package com.aiplatform.foundation.modelruntime.application.deployment.service;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ModelCatalogPermissionDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.application.deployment.command.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.command.DisableModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.query.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailability;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentAvailabilityNextStep;
import com.aiplatform.foundation.modelruntime.application.deployment.command.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.command.PublishModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentDiscoveryUnavailableException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeResult;
import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentProviderUnavailableException;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreation;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionPage;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentStateException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelDeploymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T05:00:00Z");
    private static final TenantId TENANT =
            new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION_ID = new ProviderConnectionId(
            310000000000000001L);
    private static final long DEPLOYMENT_ID = 320000000000000001L;
    private static final String DEPLOYMENT_ID_TEXT = Long.toString(DEPLOYMENT_ID);

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
                DEPLOYMENT_ID_TEXT,
                0,
                Duration.ofSeconds(2)));
        var published = service.publish(new PublishModelDeploymentCommand(
                context("model:write"),
                DEPLOYMENT_ID_TEXT,
                1));

        assertTrue(probed.reachable());
        assertEquals(ModelDeploymentStatus.VERIFIED.name(), probed.modelDeployment().status());
        assertEquals(ModelDeploymentStatus.PUBLISHED.name(), published.status());
        assertEquals(2, published.version());
    }

    @Test
    void 模型写权限可停用已发布部署且不得在原修订重新发布() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, successfulProbe());
        service.create(createCommand(context("model:write")));
        service.probe(new ProbeModelDeploymentCommand(
                context("model:write"), DEPLOYMENT_ID_TEXT, 0, Duration.ofSeconds(2)));
        service.publish(new PublishModelDeploymentCommand(
                context("model:write"), DEPLOYMENT_ID_TEXT, 1));

        var disabled = service.disable(new DisableModelDeploymentCommand(
                context("model:write"), DEPLOYMENT_ID_TEXT, 2));

        assertEquals(ModelDeploymentStatus.DISABLED.name(), disabled.status());
        assertEquals(ModelDeploymentAvailability.UNAVAILABLE, disabled.availability());
        assertEquals(ModelDeploymentAvailabilityNextStep.NONE, disabled.nextStep());
        assertEquals(3, disabled.version());
        assertThrows(
                ModelDeploymentStateException.class,
                () -> service.publish(new PublishModelDeploymentCommand(
                        context("model:write"), DEPLOYMENT_ID_TEXT, 3)));
    }

    @Test
    void 列表必须以当前连接修订和验证有效期投影部署可用性() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, successfulProbe());
        service.create(createCommand(context("model:write")));
        service.probe(new ProbeModelDeploymentCommand(
                context("model:write"), DEPLOYMENT_ID_TEXT, 0, Duration.ofSeconds(2)));
        service.publish(new PublishModelDeploymentCommand(
                context("model:write"), DEPLOYMENT_ID_TEXT, 1));

        var healthy = service.list(new ListModelDeploymentsQuery(
                context("model:read"), new PageQuery(1, 20)));
        assertEquals(ModelDeploymentAvailability.HEALTHY, healthy.items().getFirst().availability());
        assertEquals(ModelDeploymentAvailabilityNextStep.NONE, healthy.items().getFirst().nextStep());

        providers.connection = providers.connection.recordSuccessfulProbe(
                "DETERMINISTIC_OK", NOW.plusSeconds(1));

        var unavailable = service.list(new ListModelDeploymentsQuery(
                context("model:read"), new PageQuery(1, 20)));
        assertEquals(ModelDeploymentAvailability.UNAVAILABLE, unavailable.items().getFirst().availability());
        assertEquals(ModelDeploymentAvailabilityNextStep.VERIFY_CONNECTION, unavailable.items().getFirst().nextStep());

        providers.connection = verifiedProvider();
        deployments.deployment = ModelDeployment.reconstitute(
                TENANT,
                new ModelDeploymentId(DEPLOYMENT_ID),
                CONNECTION_ID,
                1,
                "deterministic-chat",
                "演示对话模型",
                ModelType.CHAT,
                Set.of(ModelCapability.CHAT),
                8192,
                1024,
                null,
                new ModelCapabilityVerificationId(330000000000000001L),
                NOW.minusSeconds(1),
                ModelDeploymentStatus.PUBLISHED,
                2,
                NOW.minusSeconds(60));

        var degraded = service.list(new ListModelDeploymentsQuery(
                context("model:read"), new PageQuery(1, 20)));
        assertEquals(ModelDeploymentAvailability.DEGRADED, degraded.items().getFirst().availability());
        assertEquals(ModelDeploymentAvailabilityNextStep.REPROBE_MODEL, degraded.items().getFirst().nextStep());
    }

    @Test
    void 能力不匹配时保持草稿且不写入新版本() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, probe(Set.of(ModelCapability.EMBEDDING)));
        service.create(createCommand(context("model:write")));

        var result = service.probe(new ProbeModelDeploymentCommand(
                context("model:write"),
                DEPLOYMENT_ID_TEXT,
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
                        DEPLOYMENT_ID_TEXT,
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

    @Test
    void 当前连接修订不存在有效发现候选时不得创建部署() {
        var deployments = new InMemoryDeploymentRepository();
        var providers = new InMemoryProviderRepository(verifiedProvider());
        var service = service(deployments, providers, successfulProbe(),
                (tenantId, providerId, revision, modelCode, modelType, now) -> Optional.empty());

        assertThrows(
                ModelDeploymentDiscoveryUnavailableException.class,
                () -> service.create(createCommand(context("model:write"))));
        assertTrue(deployments.findById(TENANT, new ModelDeploymentId(DEPLOYMENT_ID)).isEmpty());
    }

    private static ModelDeploymentService service(
            InMemoryDeploymentRepository deployments,
            InMemoryProviderRepository providers,
            ProviderProbeResult probeResult) {
        return service(
                deployments,
                providers,
                probeResult,
                (tenantId, providerId, revision, modelCode, modelType, now) -> Optional.of(
                        new DiscoveredModel(
                                modelCode, modelType, Set.of(ModelCapability.CHAT))));
    }

    private static ModelDeploymentService service(
            InMemoryDeploymentRepository deployments,
            InMemoryProviderRepository providers,
            ProviderProbeResult probeResult,
            ProviderModelDiscoveryLookup discoveries) {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new ModelDeploymentService(
                deployments,
                providers,
                discoveries,
                new ProbeProviderService(
                        List.of(new ProviderConnectionProbe() {
                            @Override
                            public ProviderKind providerKind() {
                                return ProviderKind.DETERMINISTIC;
                            }

                            @Override
                            public com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation probe(
                                    com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest request) {
                                return new com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeObservation(
                                        probeResult.reachable(),
                                        probeResult.capabilities(),
                                        probeResult.elapsed(),
                                        probeResult.detailCode());
                            }
                        }),
                        clock),
                verificationStore(deployments),
                clock,
                () -> DEPLOYMENT_ID,
                6,
                Duration.ofHours(24));
    }

    private static ModelDeploymentVerificationStore verificationStore(
            InMemoryDeploymentRepository deployments) {
        return (result, expectedVersion) -> {
            if (result.verified()
                    && !deployments.update(result.revisedDeployment(), expectedVersion)) {
                throw new IllegalStateException("模型部署版本已改变");
            }
        };
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
                CONNECTION_ID.toString(),
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
                        new CredentialReference("secret://demo/provider"),
                        NOW.minusSeconds(60))
                .recordSuccessfulProbe("DETERMINISTIC_OK", NOW.minusSeconds(30));
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                TENANT,
                new SubjectId(1843651857942052866L),
                new MembershipId(3382738893456539650L),
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
        public ModelDeploymentPage findPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            var items = deployment == null ? List.<ModelDeployment>of() : List.of(deployment);
            return new ModelDeploymentPage(
                    items,
                    items.size());
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
        public ProviderConnectionPage findPage(
                TenantId tenantId,
                long pageNumber,
                long pageSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(ProviderConnection updated, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }
}
