package com.aiplatform.foundation.provider.deterministic.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DeterministicProviderAutoConfigurationTest {

    @Test
    void 自动注册演示供应商的四类扩展能力() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DeterministicProviderAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderConnectionProbe.class);
                    assertThat(context).hasSingleBean(ProviderModelDiscoverer.class);
                    assertThat(context).hasSingleBean(ChatModelFactory.class);
                    assertThat(context).hasSingleBean(EmbeddingModelFactory.class);
                });
    }
}
