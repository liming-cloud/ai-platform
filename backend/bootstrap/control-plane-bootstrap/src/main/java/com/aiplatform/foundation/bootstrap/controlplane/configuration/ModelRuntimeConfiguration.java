package com.aiplatform.foundation.bootstrap.controlplane.configuration;

import com.aiplatform.foundation.modelruntime.api.extension.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.api.extension.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.api.extension.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.api.invocation.ModelRuntimeResolver;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.application.port.out.ProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.application.runtime.DefaultModelRuntimeResolver;
import com.aiplatform.foundation.provider.deterministic.DeterministicChatModelFactory;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModelFactory;
import com.aiplatform.foundation.provider.ollama.OllamaChatModelFactory;
import com.aiplatform.foundation.provider.ollama.OllamaConnectionProbe;
import com.aiplatform.foundation.provider.ollama.OllamaEmbeddingModelFactory;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleChatModelFactory;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleConnectionProbe;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleEmbeddingModelFactory;
import com.aiplatform.foundation.security.egress.AllowlistedOutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.PublicHttpsEndpointPolicy;
import com.aiplatform.foundation.security.secret.EnvironmentSecretResolver;
import com.aiplatform.foundation.security.secret.SecretResolver;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 控制面使用的多供应商模型工厂、安全边界与修订缓存显式装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "foundation.model-runtime.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ModelRuntimeConfiguration {

    @Bean
    SecretResolver modelSecretResolver() {
        return new EnvironmentSecretResolver();
    }

    @Bean
    OutboundEndpointPolicy modelOutboundEndpointPolicy(
            @Value("${foundation.model-runtime.allowed-endpoints:}") String allowedEndpoints) {
        return new AllowlistedOutboundEndpointPolicy(
                parseAllowedEndpoints(allowedEndpoints),
                new PublicHttpsEndpointPolicy());
    }

    @Bean
    ProviderConnectionProbe openAiCompatibleConnectionProbe(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy) {
        return new OpenAiCompatibleConnectionProbe(secrets, endpointPolicy);
    }

    @Bean
    ProviderConnectionProbe ollamaConnectionProbe(OutboundEndpointPolicy endpointPolicy) {
        return new OllamaConnectionProbe(endpointPolicy);
    }

    @Bean
    ChatModelFactory deterministicChatModelFactory() {
        return new DeterministicChatModelFactory();
    }

    @Bean
    EmbeddingModelFactory deterministicEmbeddingModelFactory() {
        return new DeterministicEmbeddingModelFactory();
    }

    @Bean
    ChatModelFactory openAiCompatibleChatModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            @Value("${foundation.model-runtime.call-timeout:PT30S}") Duration timeout) {
        return new OpenAiCompatibleChatModelFactory(secrets, endpointPolicy, timeout);
    }

    @Bean
    EmbeddingModelFactory openAiCompatibleEmbeddingModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            @Value("${foundation.model-runtime.call-timeout:PT30S}") Duration timeout) {
        return new OpenAiCompatibleEmbeddingModelFactory(secrets, endpointPolicy, timeout);
    }

    @Bean
    ChatModelFactory ollamaChatModelFactory(OutboundEndpointPolicy endpointPolicy) {
        return new OllamaChatModelFactory(endpointPolicy);
    }

    @Bean
    EmbeddingModelFactory ollamaEmbeddingModelFactory(
            OutboundEndpointPolicy endpointPolicy) {
        return new OllamaEmbeddingModelFactory(endpointPolicy);
    }

    @Bean
    @ConditionalOnBean({
            ModelDeploymentRepository.class,
            ProviderConnectionRepository.class
    })
    ModelRuntimeResolver modelRuntimeResolver(
            ModelDeploymentRepository deployments,
            ProviderConnectionRepository providers,
            List<ChatModelFactory> chatFactories,
            List<EmbeddingModelFactory> embeddingFactories,
            @Value("${foundation.model-runtime.cache.maximum-size:64}") int maximumCacheSize,
            @Value("${foundation.model-runtime.cache.idle-timeout:PT30M}") Duration idleTimeout) {
        return new DefaultModelRuntimeResolver(
                deployments,
                providers,
                chatFactories,
                embeddingFactories,
                maximumCacheSize,
                idleTimeout,
                Clock.systemUTC());
    }

    private static Set<URI> parseAllowedEndpoints(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(endpoint -> !endpoint.isEmpty())
                .map(URI::create)
                .collect(Collectors.toUnmodifiableSet());
    }
}
