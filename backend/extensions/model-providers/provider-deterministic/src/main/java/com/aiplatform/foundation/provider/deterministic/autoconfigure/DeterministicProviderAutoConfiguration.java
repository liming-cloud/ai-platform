package com.aiplatform.foundation.provider.deterministic.autoconfigure;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.provider.deterministic.DeterministicChatModelFactory;
import com.aiplatform.foundation.provider.deterministic.DeterministicEmbeddingModelFactory;
import com.aiplatform.foundation.provider.deterministic.DeterministicProviderConnectionProbe;
import com.aiplatform.foundation.provider.deterministic.DeterministicPlatformProviderConnectionProbe;
import com.aiplatform.foundation.provider.deterministic.DeterministicPlatformProviderModelDiscoverer;
import com.aiplatform.foundation.provider.deterministic.DeterministicProviderModelDiscoverer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 自动注册确定性演示供应商的探测、对话和嵌入能力。
 */
@AutoConfiguration
public class DeterministicProviderAutoConfiguration {

    @Bean
    ProviderConnectionProbe deterministicProviderConnectionProbe() {
        return new DeterministicProviderConnectionProbe();
    }

    @Bean
    PlatformProviderConnectionProbe deterministicPlatformProviderConnectionProbe() {
        return new DeterministicPlatformProviderConnectionProbe();
    }

    @Bean
    PlatformProviderModelDiscoverer deterministicPlatformProviderModelDiscoverer() {
        return new DeterministicPlatformProviderModelDiscoverer();
    }

    @Bean
    ProviderModelDiscoverer deterministicProviderModelDiscoverer() {
        return new DeterministicProviderModelDiscoverer();
    }

    @Bean
    ChatModelFactory deterministicChatModelFactory() {
        return new DeterministicChatModelFactory();
    }

    @Bean
    EmbeddingModelFactory deterministicEmbeddingModelFactory() {
        return new DeterministicEmbeddingModelFactory();
    }
}
