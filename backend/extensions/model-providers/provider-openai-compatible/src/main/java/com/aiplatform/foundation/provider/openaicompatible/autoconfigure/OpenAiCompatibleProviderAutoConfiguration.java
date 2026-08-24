package com.aiplatform.foundation.provider.openaicompatible.autoconfigure;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.infrastructure.configuration.ModelRuntimeExecutionAutoConfiguration;
import com.aiplatform.foundation.modelruntime.infrastructure.configuration.ModelRuntimePersistenceAutoConfiguration;
import com.aiplatform.foundation.modelruntime.infrastructure.configuration.ModelRuntimeProperties;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleChatModelFactory;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleConnectionProbe;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatiblePlatformProviderModelDiscoverer;
import com.aiplatform.foundation.provider.openaicompatible.VolcengineArkPlatformProviderModelDiscoverer;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleEmbeddingModelFactory;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * 在密钥和出站策略就绪时注册开放兼容协议能力。
 */
@AutoConfiguration(after = {
    ModelRuntimeExecutionAutoConfiguration.class,
    ModelRuntimePersistenceAutoConfiguration.class
})
@ConditionalOnBean(OutboundEndpointPolicy.class)
public class OpenAiCompatibleProviderAutoConfiguration {

    @Bean
    @ConditionalOnBean(SecretResolver.class)
    ProviderConnectionProbe openAiCompatibleConnectionProbe(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy) {
        return new OpenAiCompatibleConnectionProbe(secrets, endpointPolicy);
    }

    @Bean
    @ConditionalOnBean(PlatformProviderCredentialResolver.class)
    PlatformProviderModelDiscoverer openAiCompatiblePlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver secrets,
            OutboundEndpointPolicy endpointPolicy) {
        return new OpenAiCompatiblePlatformProviderModelDiscoverer(secrets, endpointPolicy);
    }

    @Bean
    @ConditionalOnBean(PlatformProviderCredentialResolver.class)
    PlatformProviderModelDiscoverer volcengineArkPlatformProviderModelDiscoverer(
            PlatformProviderCredentialResolver secrets,
            OutboundEndpointPolicy endpointPolicy) {
        return new VolcengineArkPlatformProviderModelDiscoverer(secrets, endpointPolicy);
    }

    @Bean
    @ConditionalOnBean(SecretResolver.class)
    ChatModelFactory openAiCompatibleChatModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            ModelRuntimeProperties properties) {
        return new OpenAiCompatibleChatModelFactory(
                secrets,
                endpointPolicy,
                properties.getCallTimeout());
    }

    @Bean
    @ConditionalOnBean(SecretResolver.class)
    EmbeddingModelFactory openAiCompatibleEmbeddingModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            ModelRuntimeProperties properties) {
        return new OpenAiCompatibleEmbeddingModelFactory(
                secrets,
                endpointPolicy,
                properties.getCallTimeout());
    }
}
