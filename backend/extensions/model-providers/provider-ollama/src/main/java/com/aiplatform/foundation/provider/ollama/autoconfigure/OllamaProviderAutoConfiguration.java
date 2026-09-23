package com.aiplatform.foundation.provider.ollama.autoconfigure;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.provider.ollama.OllamaChatModelFactory;
import com.aiplatform.foundation.provider.ollama.OllamaConnectionProbe;
import com.aiplatform.foundation.provider.ollama.OllamaEmbeddingModelFactory;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * 在出站策略就绪时注册 Ollama 探测、对话和嵌入能力。
 */
@AutoConfiguration
@ConditionalOnBean(OutboundEndpointPolicy.class)
public class OllamaProviderAutoConfiguration {

    @Bean
    ProviderConnectionProbe ollamaConnectionProbe(
            OutboundEndpointPolicy endpointPolicy) {
        return new OllamaConnectionProbe(endpointPolicy);
    }

    @Bean
    ChatModelFactory ollamaChatModelFactory(
            OutboundEndpointPolicy endpointPolicy) {
        return new OllamaChatModelFactory(endpointPolicy);
    }

    @Bean
    EmbeddingModelFactory ollamaEmbeddingModelFactory(
            OutboundEndpointPolicy endpointPolicy) {
        return new OllamaEmbeddingModelFactory(endpointPolicy);
    }
}
