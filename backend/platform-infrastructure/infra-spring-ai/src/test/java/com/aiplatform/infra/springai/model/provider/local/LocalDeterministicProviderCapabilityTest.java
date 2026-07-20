package com.aiplatform.infra.springai.model.provider.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class LocalDeterministicProviderCapabilityTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalRuntimeConfiguration.class);

    @Test
    void keepsLocalRuntimeFactoriesAndProbeDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(LocalDeterministicSpringAiModelOptionsStrategy.class);
            assertThat(context).doesNotHaveBean(LocalDeterministicChatModelFactory.class);
            assertThat(context).doesNotHaveBean(LocalDeterministicEmbeddingModelFactory.class);
            assertThat(context).doesNotHaveBean(LocalDeterministicSpringAiModelProbeClient.class);
        });
    }

    @Test
    void enablesTheCompleteLocalRuntimeOnlyWhenCapabilityIsExplicitlyTrue() {
        contextRunner
                .withPropertyValues("aip.model-hub.local-deterministic-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(LocalDeterministicSpringAiModelOptionsStrategy.class);
                    assertThat(context).hasSingleBean(LocalDeterministicChatModelFactory.class);
                    assertThat(context).hasSingleBean(LocalDeterministicEmbeddingModelFactory.class);
                    assertThat(context).hasSingleBean(LocalDeterministicSpringAiModelProbeClient.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            LocalDeterministicSpringAiModelOptionsStrategy.class,
            LocalDeterministicChatModelFactory.class,
            LocalDeterministicEmbeddingModelFactory.class,
            LocalDeterministicSpringAiModelProbeClient.class
    })
    static class LocalRuntimeConfiguration {
    }
}
