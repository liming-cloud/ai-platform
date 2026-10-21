package com.aiplatform.foundation.provider.openaicompatible.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.modelruntime.application.probe.port.out.ProviderConnectionProbe;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoverer;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilitySampleExecutor;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ModelRuntimeCallSettings;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiCompatibleProviderAutoConfigurationTest {

    @Test
    void 仅在密钥和出站策略齐全时注册扩展() {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OpenAiCompatibleProviderAutoConfiguration.class))
                .withBean(ModelRuntimeCallSettings.class,
                        () -> new ModelRuntimeCallSettings(Duration.ofSeconds(30)))
                .withBean(
                        OutboundEndpointPolicy.class,
                        () -> mock(OutboundEndpointPolicy.class));

        runner.run(context -> assertThat(context)
                .doesNotHaveBean(ChatModelFactory.class));

        runner.withBean(SecretResolver.class, () -> mock(SecretResolver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProviderConnectionProbe.class);
                    assertThat(context).doesNotHaveBean(PlatformProviderModelDiscoverer.class);
                    assertThat(context).hasSingleBean(ChatModelFactory.class);
                    assertThat(context).hasSingleBean(EmbeddingModelFactory.class);
                });

        runner.withBean(SecretResolver.class, () -> mock(SecretResolver.class))
                .withBean(PlatformProviderCredentialResolver.class,
                        () -> mock(PlatformProviderCredentialResolver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBeans(PlatformProviderModelDiscoverer.class).hasSize(3);
                });
    }

    @Test
    void 平台能力探测只依赖平台凭据不能借用租户解析器() {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenAiCompatibleProviderAutoConfiguration.class))
                .withBean(OutboundEndpointPolicy.class, () -> mock(OutboundEndpointPolicy.class));
        runner.withBean(SecretResolver.class, () -> mock(SecretResolver.class))
                .run(context -> assertThat(context).doesNotHaveBean(PlatformCapabilitySampleExecutor.class));
        runner.withBean(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilityCredentialResolver.class, () -> mock(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilityCredentialResolver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PlatformCapabilitySampleExecutor.class);
                    assertThat(context).hasSingleBean(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformReranker.class);
                    assertThat(context).doesNotHaveBean(ProviderConnectionProbe.class);
                });
    }
}
