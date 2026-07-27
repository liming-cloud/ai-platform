package com.aiplatform.foundation.bootstrap.controlplane.configuration;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.ModelDeploymentMapper;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.PostgresModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.PostgresProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.adapter.out.persistence.ProviderConnectionMapper;
import com.aiplatform.foundation.modelruntime.api.catalog.ModelDeploymentUseCase;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.api.catalog.PublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.api.catalog.ProviderConnectionUseCase;
import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.probe.ProbeProviderUseCase;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.DefaultModelDeploymentService;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.DefaultPublishedChatDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.catalog.deployment.DefaultPublishedEmbeddingDeploymentLookup;
import com.aiplatform.foundation.modelruntime.application.catalog.provider.DefaultProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.probe.DefaultProbeProviderService;
import com.aiplatform.foundation.provider.deterministic.DeterministicProviderConnectionProbe;
import java.time.Clock;
import java.util.List;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 控制面使用的模型目录与供应商探测显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.model-catalog.enabled",
        havingValue = "true",
        matchIfMissing = true)
@MapperScan(basePackageClasses = ProviderConnectionMapper.class)
public class ModelCatalogConfiguration {

    @Bean
    ProviderConnectionRepository providerConnectionRepository(
            ProviderConnectionMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresProviderConnectionRepository(mapper, transactions);
    }

    @Bean
    ModelDeploymentRepository modelDeploymentRepository(
            ModelDeploymentMapper mapper,
            PlatformTransactionManager transactions) {
        return new PostgresModelDeploymentRepository(mapper, transactions);
    }

    @Bean
    PublishedChatDeploymentLookup publishedChatDeploymentLookup(
            ModelDeploymentRepository repository) {
        return new DefaultPublishedChatDeploymentLookup(repository);
    }

    @Bean
    PublishedEmbeddingDeploymentLookup publishedEmbeddingDeploymentLookup(
            ModelDeploymentRepository repository) {
        return new DefaultPublishedEmbeddingDeploymentLookup(repository);
    }

    @Bean
    ProviderConnectionProbe deterministicProviderConnectionProbe() {
        return new DeterministicProviderConnectionProbe();
    }

    @Bean
    ProbeProviderUseCase probeProviderUseCase(List<ProviderConnectionProbe> probes) {
        return new DefaultProbeProviderService(probes, Clock.systemUTC());
    }

    @Bean
    ProviderConnectionUseCase providerConnectionUseCase(
            ProviderConnectionRepository repository,
            ProbeProviderUseCase probes,
            EntityIdGenerator identifiers,
            @Value("${foundation.demo.provider-connection-limit:3}") int tenantLimit) {
        return new DefaultProviderConnectionService(
                repository,
                probes,
                Clock.systemUTC(),
                identifiers,
                tenantLimit);
    }

    @Bean
    ModelDeploymentUseCase modelDeploymentUseCase(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            ProbeProviderUseCase probes,
            EntityIdGenerator identifiers,
            @Value("${foundation.demo.model-deployment-limit:6}") int tenantLimit) {
        return new DefaultModelDeploymentService(
                deployments,
                providers,
                probes,
                Clock.systemUTC(),
                identifiers,
                tenantLimit);
    }
}
