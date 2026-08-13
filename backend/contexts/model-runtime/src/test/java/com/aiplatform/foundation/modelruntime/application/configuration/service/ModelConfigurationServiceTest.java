package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationTransaction;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelConfigurationServiceTest {

    private static final PlatformModelDeploymentId SOURCE_DEPLOYMENT_ID = new PlatformModelDeploymentId(7001L);
    private static final PlatformProviderConnectionId SOURCE_CONNECTION_ID = new PlatformProviderConnectionId(6001L);

    @Test
    void 个人配置必须从可信上下文绑定成员且不能借平台权限创建() {
        var repository = new InMemoryRepository();
        repository.catalog = publishedCatalog();
        var service = service(repository);

        assertThrows(IllegalStateException.class, () -> service.create(
                context("model-platform:write"),
                ModelConfigurationScope.PERSONAL,
                "我的模型",
                new ControlledModelCatalogEntryId(3001L)));

        var created = service.create(
                context("model-personal:write"),
                ModelConfigurationScope.PERSONAL,
                "我的模型",
                new ControlledModelCatalogEntryId(3001L));

        assertEquals(3382738893456539650L, created.ownerMembershipId().value());
        assertEquals(100000000000000001L, created.tenantId().value());
    }

    @Test
    void 平台目录来源失效时不得建立新模型配置() {
        var repository = new InMemoryRepository();
        repository.catalog = publishedCatalog();
        var deployments = mock(PlatformModelDeploymentRepository.class);
        when(deployments.findById(SOURCE_DEPLOYMENT_ID)).thenReturn(Optional.empty());
        var service = service(repository, deployments, mock(PlatformProviderConnectionRepository.class));

        assertThrows(IllegalStateException.class, () -> service.create(
                context("model-tenant:write"), ModelConfigurationScope.TENANT, "租户模型",
                new ControlledModelCatalogEntryId(3001L)));
        assertEquals(null, repository.configuration);
    }

    @Test
    void 创建模型配置时在同一事务写入加密凭据且不把秘密带入返回聚合() {
        var repository = new InMemoryRepository();
        repository.catalog = publishedCatalog();
        var credentials = new CapturingCredentialWriter();
        var service = new ModelConfigurationCredentialService(
                service(repository),
                credentials,
                new ImmediateTransaction());

        ModelConfiguration created;
        try (var secret = SecretValue.copyOf("test-api-key".toCharArray())) {
            created = service.create(
                    context("model-tenant:write"),
                    ModelConfigurationScope.TENANT,
                    "租户模型",
                    new ControlledModelCatalogEntryId(3001L),
                    secret);
        }

        assertEquals(created.id(), credentials.configurationId);
        assertEquals("test-api-key", credentials.value);
        assertEquals(ModelConfigurationScope.TENANT, created.scope());
    }

    @Test
    void 个人模型配置只能由同一成员轮换凭据() {
        var repository = new InMemoryRepository();
        repository.catalog = publishedCatalog();
        repository.configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L),
                ModelConfigurationScope.PERSONAL,
                new TenantId(100000000000000001L),
                new ModelConfigurationOwnerId(3382738893456539650L),
                "我的模型",
                new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));
        var credentials = new CapturingCredentialWriter();
        var service = new ModelConfigurationCredentialService(
                service(repository), credentials, new ImmediateTransaction());

        try (var secret = SecretValue.copyOf("test-api-key".toCharArray())) {
            assertThrows(
                    com.aiplatform.foundation.modelruntime.application.configuration.exception.ModelConfigurationNotFoundException.class,
                    () -> service.rotate(
                            context("model-personal:write", 3382738893456539651L),
                            new ModelConfigurationId(4001L),
                            secret));
        }

        assertEquals(null, credentials.configurationId);
    }

    @Test
    void 管理者必须显式启用草稿模型配置后才可供运行时使用() {
        var repository = new InMemoryRepository();
        repository.catalog = publishedCatalog();
        repository.configuration = ModelConfiguration.create(
                new ModelConfigurationId(4001L), ModelConfigurationScope.PLATFORM,
                null, null, "平台模型", new ControlledModelCatalogEntryId(3001L),
                Instant.parse("2026-07-28T00:00:00Z"));

        var active = service(repository).activate(
                context("model-platform:write"), new ModelConfigurationId(4001L));

        assertEquals("ACTIVE", active.status().name());
        assertEquals(1L, active.version());
        assertEquals(active, repository.configuration);
    }

    @Test
    void 管理者停用活动模型配置后状态立即不可用于运行时() {
        var repository = new InMemoryRepository();
        repository.catalog = publishedCatalog();
        repository.configuration = ModelConfiguration.create(
                        new ModelConfigurationId(4001L), ModelConfigurationScope.PLATFORM,
                        null, null, "平台模型", new ControlledModelCatalogEntryId(3001L),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .activate();

        var disabled = service(repository).disable(
                context("model-platform:write"), new ModelConfigurationId(4001L));

        assertEquals("DISABLED", disabled.status().name());
        assertEquals(2L, disabled.version());
        assertEquals(disabled, repository.configuration);
    }

    private static ModelConfigurationService service(InMemoryRepository repository) {
        var deployments = mock(PlatformModelDeploymentRepository.class);
        var connections = mock(PlatformProviderConnectionRepository.class);
        var deployment = mock(com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment.class);
        var connection = mock(PlatformProviderConnection.class);
        when(deployment.version()).thenReturn(2L);
        when(deployment.status()).thenReturn(PlatformModelDeploymentStatus.PUBLISHED);
        when(deployment.verificationExpiresAt()).thenReturn(Instant.parse("2026-07-29T00:00:00Z"));
        when(deployment.providerConnectionId()).thenReturn(SOURCE_CONNECTION_ID);
        when(deployment.providerConnectionRevision()).thenReturn(3L);
        when(connection.version()).thenReturn(3L);
        when(connection.status()).thenReturn(ProviderConnectionStatus.VERIFIED);
        when(deployments.findById(SOURCE_DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(connections.findById(SOURCE_CONNECTION_ID)).thenReturn(Optional.of(connection));
        return service(repository, deployments, connections);
    }

    private static ModelConfigurationService service(
            InMemoryRepository repository,
            PlatformModelDeploymentRepository deployments,
            PlatformProviderConnectionRepository connections) {
        return new ModelConfigurationService(
                repository,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC),
                () -> 4001L,
                deployments,
                connections);
    }

    private static ControlledModelCatalogEntry publishedCatalog() {
        return ControlledModelCatalogEntry.createFromPlatformDeployment(
                new ControlledModelCatalogEntryId(3001L),
                SOURCE_DEPLOYMENT_ID,
                2L,
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"),
                "test-chat-model",
                com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                Instant.parse("2026-07-28T00:00:00Z")).publish();
    }

    private static TrustedRequestContext context(String permission) {
        return context(permission, 3382738893456539650L);
    }

    private static TrustedRequestContext context(String permission, long membershipId) {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(1843651857942052866L),
                new MembershipId(membershipId),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trace-model-configuration");
    }

    private static final class InMemoryRepository implements ModelConfigurationRepository {
        private ControlledModelCatalogEntry catalog;
        private ModelConfiguration configuration;

        @Override
        public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
            return catalog != null && catalog.id().equals(id) ? Optional.of(catalog) : Optional.empty();
        }

        @Override
        public void saveCatalogEntry(ControlledModelCatalogEntry entry) {
            catalog = entry;
        }

        @Override
        public void updateCatalogEntry(ControlledModelCatalogEntry entry) {
            catalog = entry;
        }

        @Override
        public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
            return configuration != null && configuration.id().equals(id)
                    ? Optional.of(configuration)
                    : Optional.empty();
        }

        @Override
        public void updateConfiguration(ModelConfiguration value) {
            configuration = value;
        }

        @Override
        public void save(ModelConfiguration value) {
            configuration = value;
        }
    }

    private static final class CapturingCredentialWriter implements ModelConfigurationCredentialWriter {
        private com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId configurationId;
        private String value;

        @Override
        public void rotate(
                com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId configurationId,
                SecretValue apiKey) {
            this.configurationId = configurationId;
            this.value = apiKey.use(String::new);
        }
    }

    private static final class ImmediateTransaction implements ModelConfigurationTransaction {
        @Override
        public <T> T execute(java.util.function.Supplier<T> action) {
            return action.get();
        }
    }
}
