package com.aiplatform.foundation.provider.openaicompatible.autoconfigure;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeCallSettings;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleChatModelFactory;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleConnectionProbe;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatiblePlatformProviderModelDiscoverer;
import com.aiplatform.foundation.provider.openaicompatible.VolcengineArkPlatformProviderModelDiscoverer;
import com.aiplatform.foundation.provider.openaicompatible.SiliconFlowRerankModelDiscoverer;
import com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatibleEmbeddingModelFactory;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * 在密钥和出站策略就绪时注册开放兼容协议能力。
 */
@AutoConfiguration
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
    @ConditionalOnBean(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilityCredentialResolver.class)
    com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatiblePlatformCapabilitySamples openAiCompatiblePlatformCapabilitySamples(
            com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilityCredentialResolver secrets, OutboundEndpointPolicy endpointPolicy) {
        return new com.aiplatform.foundation.provider.openaicompatible.OpenAiCompatiblePlatformCapabilitySamples(secrets, endpointPolicy);
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
    @ConditionalOnBean(PlatformProviderCredentialResolver.class)
    PlatformProviderModelDiscoverer siliconFlowRerankModelDiscoverer(
            PlatformProviderCredentialResolver secrets, OutboundEndpointPolicy endpointPolicy) {
        return new SiliconFlowRerankModelDiscoverer(secrets, endpointPolicy);
    }

    @Bean
    @ConditionalOnBean({SecretResolver.class, ModelRuntimeCallSettings.class})
    ChatModelFactory openAiCompatibleChatModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            ModelRuntimeCallSettings settings) {
        return new OpenAiCompatibleChatModelFactory(
                secrets,
                endpointPolicy,
                settings.timeout());
    }

    @Bean
    @ConditionalOnBean({SecretResolver.class, ModelRuntimeCallSettings.class})
    EmbeddingModelFactory openAiCompatibleEmbeddingModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            ModelRuntimeCallSettings settings) {
        return new OpenAiCompatibleEmbeddingModelFactory(
                secrets,
                endpointPolicy,
                settings.timeout());
    }
}
