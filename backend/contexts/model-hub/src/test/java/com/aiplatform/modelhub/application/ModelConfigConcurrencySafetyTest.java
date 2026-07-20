package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.application.command.DisableModelConfigCommand;
import com.aiplatform.modelhub.application.command.UpdateModelConfigCommand;
import com.aiplatform.modelhub.application.query.ModelConfigQueryRepository;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigCreationService;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModelConfigConcurrencySafetyTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-21T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void updateVersusDisableAllowsExactlyOneMutationFromTheSameVisibleVersion() throws Exception {
        BarrierConfigRepository configs = new BarrierConfigRepository(activeConfig(), 2);
        ModelConfigApplicationService service = service(configs, runtimeOptions(), mock(ModelUsagePolicyPublisher.class));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> update = executor.submit(() -> outcome(() -> service.updateModelConfig(updateCommand("并发更新"))));
            Future<String> disable = executor.submit(() -> outcome(() -> service.disableModelConfig(disableCommand())));

            assertThat(List.of(update.get(5, TimeUnit.SECONDS), disable.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "AIP-MDL-VERSIONCONFLICT");
        }

        assertThat(configs.current().version()).isEqualTo(1);
        assertThat(configs.current().status())
                .isIn(ModelConfigStatus.PENDING_APPROVAL, ModelConfigStatus.DEPRECATED);
    }

    @Test
    void twoUpdatesFromTheSameVisibleVersionCannotBothCommit() throws Exception {
        BarrierConfigRepository configs = new BarrierConfigRepository(activeConfig(), 2);
        ModelConfigApplicationService service = service(configs, runtimeOptions(), mock(ModelUsagePolicyPublisher.class));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> outcome(() -> service.updateModelConfig(updateCommand("更新 A"))));
            Future<String> second = executor.submit(() -> outcome(() -> service.updateModelConfig(updateCommand("更新 B"))));

            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "AIP-MDL-VERSIONCONFLICT");
        }

        assertThat(configs.current().version()).isEqualTo(1);
        assertThat(configs.current().modelName()).isIn("更新 A", "更新 B");
    }

    @Test
    void probeCompletionCannotResurrectAConfigDisabledWhileTheProbeWasInFlight() throws Exception {
        BarrierConfigRepository configs = new BarrierConfigRepository(activeConfig(), 0);
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        ModelRuntimeOptionsPort runtimeOptions = runtimeOptions();
        when(runtimeOptions.testConnectivity(any(ModelProvider.class), any(ModelConfig.class)))
                .thenAnswer(invocation -> {
                    probeStarted.countDown();
                    if (!releaseProbe.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("probe release timed out");
                    }
                    return new ModelRuntimeConnectivity(true, ModelConnectivityStatus.PASSED, "模型连接测试通过");
                });
        ModelUsagePolicyPublisher usagePolicy = mock(ModelUsagePolicyPublisher.class);
        ModelConfigApplicationService service = service(configs, runtimeOptions, usagePolicy);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> probe = executor.submit(() -> outcome(() ->
                    service.testConnectivity(TENANT_ID, "3001", "4001", 0)
            ));
            assertThat(probeStarted.await(5, TimeUnit.SECONDS)).isTrue();

            service.disableModelConfig(disableCommand());
            releaseProbe.countDown();

            assertThat(probe.get(5, TimeUnit.SECONDS)).isEqualTo("AIP-MDL-VERSIONCONFLICT");
        } finally {
            releaseProbe.countDown();
        }

        assertThat(configs.current().status()).isEqualTo(ModelConfigStatus.DEPRECATED);
        assertThat(configs.current().version()).isEqualTo(1);
        verify(usagePolicy, times(1)).publish(any(ModelProvider.class), any(ModelConfig.class));
    }

    private static ModelConfigApplicationService service(
            ModelConfigRepository configs,
            ModelRuntimeOptionsPort runtimeOptions,
            ModelUsagePolicyPublisher usagePolicy
    ) {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(provider()));
        ModelConfigApprovalPort approvals = change -> change.changeId();
        return new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvals,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicy,
                mock(AuditEventPublisher.class),
                CLOCK
        );
    }

    private static ModelRuntimeOptionsPort runtimeOptions() {
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        when(runtimeOptions.validate(any(ModelProvider.class), any(ModelConfig.class)))
                .thenReturn(ModelRuntimeValidation.passed());
        return runtimeOptions;
    }

    private static UpdateModelConfigCommand updateCommand(String modelName) {
        return new UpdateModelConfigCommand(
                TENANT_ID,
                "1001",
                "3001",
                "4001",
                "gpt-4.1-mini",
                modelName,
                "CHAT",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                Map.of("providerKind", "openai-compatible", "temperature", 0.2),
                0
        );
    }

    private static DisableModelConfigCommand disableCommand() {
        return new DisableModelConfigCommand(
                TENANT_ID,
                "1001",
                "3001",
                "4001",
                "并发安全测试",
                0
        );
    }

    private static String outcome(ThrowingAction action) throws Exception {
        try {
            action.run();
            return "SUCCESS";
        } catch (ApplicationException exception) {
            return exception.errorCode().code();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof ApplicationException applicationException) {
                return applicationException.errorCode().code();
            }
            throw exception;
        }
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TENANT_ID,
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

    private static ModelConfig activeConfig() {
        return new ModelConfig(
                TENANT_ID,
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
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible", "temperature", 0.2)),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-07-20T01:00:00Z"),
                "模型连接测试通过",
                Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T01:00:00Z")
        );
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static final class BarrierConfigRepository implements ModelConfigRepository {
        private final AtomicReference<ModelConfig> state;
        private final CyclicBarrier readBarrier;
        private final AtomicInteger barrierReads = new AtomicInteger();

        private BarrierConfigRepository(ModelConfig initial, int synchronizedReads) {
            state = new AtomicReference<>(initial);
            readBarrier = synchronizedReads > 0 ? new CyclicBarrier(synchronizedReads) : null;
        }

        private ModelConfig current() {
            return state.get();
        }

        @Override
        public Optional<ModelConfig> compareAndSet(
                ModelConfig updatedConfig,
                int expectedVersion,
                ModelConfigStatus expectedStatus
        ) {
            ModelConfig current = state.get();
            if (current.version() != expectedVersion || current.status() != expectedStatus) {
                return Optional.empty();
            }
            ModelConfig persisted = updatedConfig.withVersion(expectedVersion + 1);
            return state.compareAndSet(current, persisted) ? Optional.of(persisted) : Optional.empty();
        }

        @Override
        public Optional<ModelConfig> findById(TenantId tenantId, String providerId, String modelConfigId) {
            ModelConfig snapshot = state.get();
            if (readBarrier != null && barrierReads.incrementAndGet() <= readBarrier.getParties()) {
                try {
                    readBarrier.await(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("concurrent read barrier failed", exception);
                }
            }
            return Optional.of(snapshot);
        }

        @Override
        public Optional<ModelConfig> findByProviderAndCode(TenantId tenantId, String providerId, String modelCode) {
            return Optional.of(state.get());
        }

        @Override
        public Optional<ModelConfig> findByModelConfigId(TenantId tenantId, String modelConfigId) {
            return Optional.of(state.get());
        }

        @Override
        public List<ModelConfig> findByProviderId(TenantId tenantId, String providerId) {
            return List.of(state.get());
        }

        @Override
        public String nextModelConfigId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelConfig insert(ModelConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ModelConfig> compareAndSetApprovalDecision(
                ModelConfig decidedConfig,
                int expectedVersion,
                ModelConfigStatus expectedStatus,
                String expectedApprovalRequestId,
                String expectedEvidenceDigest,
                String expectedTargetDigest
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
