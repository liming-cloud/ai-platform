package com.aiplatform.foundation.provider.ollama.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OllamaProviderAutoConfigurationTest {

    @Test
    void 仅在出站策略存在时注册三类扩展能力() {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OllamaProviderAutoConfiguration.class));

        runner.run(context -> assertThat(context)
                .doesNotHaveBean(ChatModelFactory.class));

        runner.withBean(
                        OutboundEndpointPolicy.class,
                        () -> mock(OutboundEndpointPolicy.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderConnectionProbe.class);
                    assertThat(context).hasSingleBean(ChatModelFactory.class);
                    assertThat(context).hasSingleBean(EmbeddingModelFactory.class);
                });
    }
}
