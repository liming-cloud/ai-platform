package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import com.aiplatform.foundation.modelruntime.application.runtime.DefaultModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.domain.deployment.repository.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.provider.repository.ProviderConnectionRepository;
import com.aiplatform.foundation.security.egress.AllowlistedOutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.PublicHttpsEndpointPolicy;
import com.aiplatform.foundation.security.secret.EnvironmentSecretResolver;
import com.aiplatform.foundation.security.secret.SecretResolver;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册模型工厂运行所需的密钥、出站策略和修订缓存解析器。
 */
@AutoConfiguration(after = ModelRuntimePersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.model-runtime.enabled",
        havingValue = "true")
@EnableConfigurationProperties(ModelRuntimeProperties.class)
public class ModelRuntimeExecutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SecretResolver modelSecretResolver() {
        return new EnvironmentSecretResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    OutboundEndpointPolicy modelOutboundEndpointPolicy(
            ModelRuntimeProperties properties) {
        return new AllowlistedOutboundEndpointPolicy(
                Set.copyOf(properties.getAllowedEndpoints()),
                new PublicHttpsEndpointPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({
        ModelDeploymentRepository.class,
        ProviderConnectionRepository.class
    })
    ModelRuntimeResolver modelRuntimeResolver(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            List<ChatModelFactory> chatFactories,
            List<EmbeddingModelFactory> embeddingFactories,
            ModelRuntimeProperties properties) {
        return new DefaultModelRuntimeResolver(
                deployments,
                providers,
                chatFactories,
                embeddingFactories,
                properties.getCacheMaximumSize(),
                properties.getCacheIdleTimeout(),
                Clock.systemUTC());
    }
}
