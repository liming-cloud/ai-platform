package com.aiplatform.foundation.modelruntime.application.configuration.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsableModelConfigurationLookupServiceTest {

    private static final TenantId TENANT_ID = new TenantId(2001L);
    private static final ModelConfigurationId CONFIGURATION_ID = new ModelConfigurationId(4001L);

    @Test
    void 仅返回已授权且具备凭据的活动嵌入配置快照() {
        var repository = new ConfigurationRepository(activePersonalEmbedding());
        var service = new UsableModelConfigurationLookupService(
                repository,
                new ModelConfigurationAccessService(repository, new AccessRepository(), () -> 5001L, java.time.Clock.systemUTC()),
                configurationId -> configurationId.equals(CONFIGURATION_ID));

        var result = service.find(member(6001L),
                new com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId(CONFIGURATION_ID.value()),
                com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING);

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().configurationId().value() == CONFIGURATION_ID.value());
        assertTrue(result.orElseThrow().capabilities().embeddingDimension() == 1024);
    }

    @Test
    void 不同成员不能解析个人配置且缺失凭据也必须失败关闭() {
        var repository = new ConfigurationRepository(activePersonalEmbedding());
        var access = new ModelConfigurationAccessService(
                repository, new AccessRepository(), () -> 5001L, java.time.Clock.systemUTC());
        var noCredential = new UsableModelConfigurationLookupService(
                repository, access, configurationId -> false);
        var configured = new UsableModelConfigurationLookupService(
                repository, access, configurationId -> true);

        assertFalse(noCredential.find(member(6001L),
                new com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId(CONFIGURATION_ID.value()),
                com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING).isPresent());
        assertFalse(configured.find(member(6002L),
                new com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId(CONFIGURATION_ID.value()),
                com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING).isPresent());
    }

    @Test
    void 模型类型与目录不一致时不得把配置作为嵌入模型使用() {
        var repository = new ConfigurationRepository(activePersonalEmbedding());
        var service = new UsableModelConfigurationLookupService(
                repository,
                new ModelConfigurationAccessService(repository, new AccessRepository(), () -> 5001L, java.time.Clock.systemUTC()),
                configurationId -> true);

        assertFalse(service.find(member(6001L),
                new com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId(CONFIGURATION_ID.value()),
                com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.CHAT).isPresent());
    }

    @Test
    void 未发布目录条目不得被解析为可用模型配置() {
        var repository = new ConfigurationRepository(
                activePersonalEmbedding(),
                ConfigurationRepository.catalog());
        var service = new UsableModelConfigurationLookupService(
                repository,
                new ModelConfigurationAccessService(repository, new AccessRepository(), () -> 5001L, java.time.Clock.systemUTC()),
                configurationId -> true);

        assertFalse(service.find(member(6001L),
                new com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelConfigurationId(CONFIGURATION_ID.value()),
                com.aiplatform.foundation.modelruntime.application.configuration.facade.ModelType.EMBEDDING).isPresent());
    }

    private static ModelConfiguration activePersonalEmbedding() {
        return ModelConfiguration.create(
                        CONFIGURATION_ID,
                        ModelConfigurationScope.PERSONAL,
                        TENANT_ID,
                        new ModelConfigurationOwnerId(6001L),
                        "个人嵌入模型",
                        new ControlledModelCatalogEntryId(3001L),
                        Instant.parse("2026-07-28T00:00:00Z"))
                .activate();
    }

    private static TrustedRequestContext member(long membershipId) {
        return new TrustedRequestContext(
                TENANT_ID,
                new SubjectId(membershipId + 10000L),
                new MembershipId(membershipId),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode("model:read")),
                "trace-model-configuration");
    }

    private static final class ConfigurationRepository implements ModelConfigurationRepository {
        private final ModelConfiguration configuration;
        private final ControlledModelCatalogEntry catalog;

        private ConfigurationRepository(ModelConfiguration configuration) {
            this(configuration, catalog().publish());
        }

        private ConfigurationRepository(
                ModelConfiguration configuration,
                ControlledModelCatalogEntry catalog) {
            this.configuration = configuration;
            this.catalog = catalog;
        }

        private static ControlledModelCatalogEntry catalog() {
            return ControlledModelCatalogEntry.create(
                        new ControlledModelCatalogEntryId(3001L),
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("https://example.com/v1"),
                        "text-embedding-v1",
                        ModelType.EMBEDDING,
                        ModelRuntimeCapabilities.embedding(8192, 1024),
                        Instant.parse("2026-07-28T00:00:00Z"));
        }

        @Override public Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id) {
            return catalog.id().equals(id) ? Optional.of(catalog) : Optional.empty();
        }
        @Override public void saveCatalogEntry(ControlledModelCatalogEntry entry) { }
        @Override public void updateCatalogEntry(ControlledModelCatalogEntry entry) { }
        @Override public Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id) {
            return configuration.id().equals(id) ? Optional.of(configuration) : Optional.empty();
        }
        @Override public void updateConfiguration(ModelConfiguration value) { }
        @Override public void save(ModelConfiguration value) { }
    }

    private static final class AccessRepository implements ModelConfigurationAccessRepository {
        @Override public Optional<ModelConfigurationAccessGrant> findById(ModelConfigurationAccessGrantId id) { return Optional.empty(); }
        @Override public boolean hasActiveGrant(ModelConfigurationId configurationId, TenantId tenantId, ModelConfigurationOwnerId membershipId) { return false; }
        @Override public void save(ModelConfigurationAccessGrant value) { }
        @Override public void update(ModelConfigurationAccessGrant value) { }
    }
}
