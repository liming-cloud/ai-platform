package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
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

    private static ModelConfigurationService service(InMemoryRepository repository) {
        return new ModelConfigurationService(
                repository,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC),
                () -> 4001L);
    }

    private static ControlledModelCatalogEntry publishedCatalog() {
        return ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/v1"),
                "qwen-plus",
                com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.CHAT,
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
        public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
            return configuration != null && configuration.id().equals(id)
                    ? Optional.of(configuration)
                    : Optional.empty();
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
