package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BoundedModelConnectivityProbeTest {
    @Test
    void cancelsAProbeThatExceedsTheConfiguredDeadline() throws Exception {
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(runtimeOptions.testConnectivity(any(ModelProvider.class), any(ModelConfig.class)))
                .thenAnswer(invocation -> {
                    try {
                        new CountDownLatch(1).await();
                        throw new AssertionError("unreachable");
                    } catch (InterruptedException exception) {
                        interrupted.countDown();
                        throw exception;
                    }
                });
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        BoundedModelConnectivityProbe probe = new BoundedModelConnectivityProbe(
                runtimeOptions,
                Duration.ofMillis(25),
                executor
        );

        try {
            ModelRuntimeConnectivity result = probe.test(provider(), config());

            assertThat(result.connected()).isFalse();
            assertThat(result.status()).isEqualTo(ModelConnectivityStatus.FAILED);
            assertThat(result.message()).contains("超时");
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            probe.close();
        }
    }

    @Test
    void rejectsAnUnboundedDeadline() {
        assertThatThrownBy(() -> new BoundedModelConnectivityProbe(
                mock(ModelRuntimeOptionsPort.class),
                Duration.ofSeconds(61),
                mock(ExecutorService.class)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不超过 60 秒");
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                ModelProviderStatus.ACTIVE,
                "REQ-PROVIDER",
                true,
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T00:00:00Z")
        );
    }

    private static ModelConfig config() {
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                ModelConfigStatus.ACTIVE,
                "REQ-CONFIG",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T00:00:00Z")
        );
    }
}
