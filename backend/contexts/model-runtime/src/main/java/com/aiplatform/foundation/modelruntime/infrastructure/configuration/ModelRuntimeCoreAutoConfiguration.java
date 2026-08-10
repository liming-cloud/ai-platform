package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.facade.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ControlledModelCatalogService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationCredentialService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationAccessService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.ModelConfigurationReadService;
import com.aiplatform.foundation.modelruntime.application.configuration.service.UsableModelConfigurationLookupService;
import com.aiplatform.foundation.modelruntime.application.configuration.facade.UsableModelConfigurationLookup;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialUser;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationTransaction;
import com.aiplatform.foundation.modelruntime.application.deployment.service.ModelDeploymentService;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.deployment.service.PublishedChatDeploymentLookupService;
import com.aiplatform.foundation.modelruntime.application.deployment.service.PublishedEmbeddingDeploymentLookupService;
import com.aiplatform.foundation.modelruntime.application.runtime.ModelConfigurationRuntimeService;
import com.aiplatform.foundation.modelruntime.application.runtime.DefaultChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatRouteExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.GovernedChatRuntime;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.probe.service.ProbeProviderService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryReadService;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryHistoryReader;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialTransaction;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationRepository;
import com.aiplatform.foundation.modelruntime.domain.configuration.repository.ModelConfigurationAccessRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册模型目录用例、探测编排和跨上下文发布查询门面。
 */
@AutoConfiguration(after = ModelRuntimePersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.model-catalog.enabled",
        havingValue = "true")
@EnableConfigurationProperties(ModelCatalogProperties.class)
public class ModelRuntimeCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProbeProviderService probeProviderService(
            List<ProviderConnectionProbe> probes) {
        return new ProbeProviderService(probes, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean({ProviderConnectionCredentialWriter.class, ProviderConnectionCredentialTransaction.class})
    @ConditionalOnMissingBean
    ProviderConnectionService providerConnectionService(
            ProviderConnectionRepository repository,
            ProbeProviderService probes,
            ProviderConnectionCredentialWriter credentials,
            ProviderConnectionCredentialTransaction transactions,
            EntityIdGenerator identifiers,
            ModelCatalogProperties properties) {
        return new ProviderConnectionService(
                repository,
                probes,
                credentials,
                transactions,
                Clock.systemUTC(),
                identifiers,
                properties.getProviderConnectionLimit());
    }

    @Bean @ConditionalOnMissingBean
    ProviderModelDiscoveryService providerModelDiscoveryService(ProviderConnectionRepository connections, List<ProviderModelDiscoverer> discoverers, ProviderModelDiscoveryStore store, EntityIdGenerator identifiers, ModelCatalogProperties properties) {
        return new ProviderModelDiscoveryService(connections, discoverers, store, Clock.systemUTC(), identifiers, properties.getCapabilityVerificationValidity());
    }

    @Bean
    @ConditionalOnMissingBean
    ProviderModelDiscoveryReadService providerModelDiscoveryReadService(
            ProviderModelDiscoveryHistoryReader reader) {
        return new ProviderModelDiscoveryReadService(reader, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    ModelDeploymentService modelDeploymentService(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            ProviderModelDiscoveryLookup discoveries,
            ProbeProviderService probes,
            ModelDeploymentVerificationStore verificationStore,
            EntityIdGenerator identifiers,
            ModelCatalogProperties properties) {
        return new ModelDeploymentService(
                deployments,
                providers,
                discoveries,
                probes,
                verificationStore,
                Clock.systemUTC(),
                identifiers,
                properties.getModelDeploymentLimit(),
                properties.getCapabilityVerificationValidity());
    }

    @Bean
    @ConditionalOnMissingBean
    ModelConfigurationService modelConfigurationService(
            ModelConfigurationRepository repository,
            EntityIdGenerator identifiers) {
        return new ModelConfigurationService(
                repository,
                Clock.systemUTC(),
                identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    ControlledModelCatalogService controlledModelCatalogService(
            ModelConfigurationRepository repository,
            EntityIdGenerator identifiers) {
        return new ControlledModelCatalogService(repository, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    ModelConfigurationAccessService modelConfigurationAccessService(
            ModelConfigurationRepository configurations,
            ModelConfigurationAccessRepository grants,
            EntityIdGenerator identifiers) {
        return new ModelConfigurationAccessService(configurations, grants, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnBean({ModelConfigurationCredentialWriter.class, ModelConfigurationTransaction.class})
    @ConditionalOnMissingBean
    ModelConfigurationCredentialService modelConfigurationCredentialService(
            ModelConfigurationService configurations,
            ModelConfigurationCredentialWriter credentials,
            ModelConfigurationTransaction transactions) {
        return new ModelConfigurationCredentialService(configurations, credentials, transactions);
    }

    @Bean
    @ConditionalOnBean(ModelConfigurationCredentialInspector.class)
    @ConditionalOnMissingBean
    ModelConfigurationReadService modelConfigurationReadService(
            ModelConfigurationRepository configurations,
            ModelConfigurationService management,
            ModelConfigurationAccessService access,
            ModelConfigurationCredentialInspector credentials) {
        return new ModelConfigurationReadService(configurations, management, access, credentials);
    }

    @Bean
    @ConditionalOnBean(ModelConfigurationCredentialInspector.class)
    @ConditionalOnMissingBean
    UsableModelConfigurationLookup usableModelConfigurationLookup(
            ModelConfigurationRepository configurations,
            ModelConfigurationAccessService access,
            ModelConfigurationCredentialInspector credentials) {
        return new UsableModelConfigurationLookupService(configurations, access, credentials);
    }

    @Bean
    @ConditionalOnBean({
        ModelConfigurationCredentialUser.class,
        UsableModelConfigurationLookup.class
    })
    @ConditionalOnMissingBean
    ModelConfigurationRuntimeService modelConfigurationRuntimeService(
            UsableModelConfigurationLookup configurations,
            ModelConfigurationRepository catalog,
            ModelConfigurationCredentialUser credentials,
            List<EmbeddingModelFactory> embeddingFactories,
            List<ChatModelFactory> chatFactories) {
        return new ModelConfigurationRuntimeService(
                configurations, catalog, credentials, embeddingFactories, chatFactories);
    }

    @Bean
    @ConditionalOnBean(GovernedChatRuntime.class)
    @ConditionalOnMissingBean
    ChatRouteExecutor chatRouteExecutor(GovernedChatRuntime chats) {
        return new DefaultChatRouteExecutor(chats);
    }

    @Bean
    @ConditionalOnMissingBean
    PublishedChatDeploymentLookup publishedChatDeploymentLookup(
            ModelDeploymentRepository repository) {
        return new PublishedChatDeploymentLookupService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    PublishedEmbeddingDeploymentLookup publishedEmbeddingDeploymentLookup(
            ModelDeploymentRepository repository) {
        return new PublishedEmbeddingDeploymentLookupService(repository);
    }
}
