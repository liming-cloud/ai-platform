package org.fujay.ai.platform.model.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.fujay.ai.platform.model.application.port.CurrentPrincipalPort;
import org.fujay.ai.platform.model.application.port.DomainIdGeneratorPort;
import org.fujay.ai.platform.model.application.port.ModelGatewayChatRequest;
import org.fujay.ai.platform.model.application.port.ModelGatewayChatResult;
import org.fujay.ai.platform.model.application.port.ModelGatewayPort;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelInvocation;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.repository.ModelInvocationRepository;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModelInvocationApplicationServiceTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void invokesConfiguredChatModelAndRecordsTraceUsageWithoutPromptOrCompletionText() {
        InMemoryModelProviderRepository providerRepository = new InMemoryModelProviderRepository();
        InMemoryModelInvocationRepository invocationRepository = new InMemoryModelInvocationRepository();
        providerRepository.seed(ModelCapabilityType.CHAT);
        ModelInvocationApplicationService service = service(providerRepository, invocationRepository, request -> {
            assertThat(request.prompt()).isEqualTo("请返回一条 smoke 响应");
            assertThat(request.apiKeyCiphertext()).isEqualTo("cipher:sk-test-secret");
            return new ModelGatewayChatResult("统一调用成功", 9, 6, 15);
        });
        TraceContext.setTraceId("trace-s5-chat");

        var result = service.invokeChat(new org.fujay.ai.platform.model.application.command.ChatInvocationCommand(
                String.valueOf(providerRepository.firstModel().id()),
                "请返回一条 smoke 响应"));

        assertThat(result.output()).isEqualTo("统一调用成功");
        assertThat(result.traceId()).isEqualTo("trace-s5-chat");
        assertThat(result.promptTokens()).isEqualTo(9);
        assertThat(result.completionTokens()).isEqualTo(6);
        assertThat(result.totalTokens()).isEqualTo(15);
        assertThat(result.costUsd()).isNull();
        assertThat(result.costMissingReason()).isEqualTo("模型计费单价未配置");
        assertThat(invocationRepository.invocations).hasSize(1);
        ModelInvocation invocation = invocationRepository.invocations.getFirst();
        assertThat(invocation.traceId()).isEqualTo("trace-s5-chat");
        assertThat(invocation.invocationKind()).isEqualTo("CHAT");
        assertThat(invocation.status()).isEqualTo("SUCCESS");
        assertThat(invocation.inputChars()).isEqualTo("请返回一条 smoke 响应".length());
        assertThat(invocation.outputChars()).isEqualTo("统一调用成功".length());
        assertThat(invocation.errorMessage()).isNull();
    }

    @Test
    void rejectsNonChatModelBeforeCallingProvider() {
        InMemoryModelProviderRepository providerRepository = new InMemoryModelProviderRepository();
        InMemoryModelInvocationRepository invocationRepository = new InMemoryModelInvocationRepository();
        providerRepository.seed(ModelCapabilityType.EMBEDDING);
        CountingGatewayPort gatewayPort = new CountingGatewayPort(
                request -> new ModelGatewayChatResult("unused", 1, 1, 2));
        ModelInvocationApplicationService service = service(providerRepository, invocationRepository, gatewayPort);

        assertThatThrownBy(() -> service.invokeChat(new org.fujay.ai.platform.model.application.command.ChatInvocationCommand(
                        String.valueOf(providerRepository.firstModel().id()),
                        "ping")))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);

        assertThat(gatewayPort.invokeCount).isZero();
        assertThat(invocationRepository.invocations).isEmpty();
    }

    private static ModelInvocationApplicationService service(
            InMemoryModelProviderRepository providerRepository,
            InMemoryModelInvocationRepository invocationRepository,
            Function<ModelGatewayChatRequest, ModelGatewayChatResult> invocationHandler) {
        return service(providerRepository, invocationRepository, new CountingGatewayPort(invocationHandler));
    }

    private static ModelInvocationApplicationService service(
            InMemoryModelProviderRepository providerRepository,
            InMemoryModelInvocationRepository invocationRepository,
            ModelGatewayPort gatewayPort) {
        return new ModelInvocationApplicationService(
                providerRepository,
                invocationRepository,
                new FixedPrincipal(),
                gatewayPort,
                new FixedIdGenerator());
    }

    private static final class FixedPrincipal implements CurrentPrincipalPort {
        @Override
        public Long currentTenantId() {
            return 1001L;
        }

        @Override
        public void requireAuthenticated() {
        }
    }

    private static final class FixedIdGenerator implements DomainIdGeneratorPort {
        private final AtomicLong sequence = new AtomicLong(9000L);

        @Override
        public Long nextId() {
            return sequence.incrementAndGet();
        }
    }

    private static final class CountingGatewayPort implements ModelGatewayPort {
        private final Function<ModelGatewayChatRequest, ModelGatewayChatResult> invocationHandler;
        private int invokeCount;

        private CountingGatewayPort(Function<ModelGatewayChatRequest, ModelGatewayChatResult> invocationHandler) {
            this.invocationHandler = invocationHandler;
        }

        @Override
        public ModelGatewayChatResult testChat(ModelGatewayChatRequest request) {
            return new ModelGatewayChatResult("unused", null, null, null);
        }

        @Override
        public ModelGatewayChatResult invokeChat(ModelGatewayChatRequest request) {
            invokeCount++;
            return invocationHandler.apply(request);
        }
    }

    private static final class InMemoryModelProviderRepository implements ModelProviderRepository {
        private final List<ModelProvider> providers = new ArrayList<>();

        private void seed(ModelCapabilityType capabilityType) {
            LocalDateTime now = LocalDateTime.now();
            ConfiguredModel model = new ConfiguredModel(
                    4001L,
                    1001L,
                    3001L,
                    capabilityType == ModelCapabilityType.CHAT ? "tencent/Hunyuan-MT-7B" : "text-embedding-3-small",
                    capabilityType == ModelCapabilityType.CHAT ? "Hunyuan-MT-7B" : "Embedding",
                    capabilityType,
                    "ENABLED",
                    new ModelParameterDefaults(
                            capabilityType == ModelCapabilityType.CHAT ? 33792 : null,
                            capabilityType == ModelCapabilityType.CHAT ? 1024 : null,
                            capabilityType == ModelCapabilityType.EMBEDDING ? 1536 : null,
                            BigDecimal.valueOf(0.2),
                            BigDecimal.valueOf(0.7),
                            null,
                            null,
                            null,
                            null),
                    now,
                    now);
            providers.add(new ModelProvider(
                    3001L,
                    1001L,
                    "SiliconFlow",
                    ProviderType.OPENAI_COMPATIBLE,
                    "https://api.siliconflow.cn",
                    "cipher:sk-test-secret",
                    "cret",
                    "ENABLED",
                    BigDecimal.ZERO,
                    now,
                    now,
                    List.of(model)));
        }

        private ConfiguredModel firstModel() {
            return providers.getFirst().models().getFirst();
        }

        @Override
        public void save(ModelProvider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ModelProvider> findById(Long tenantId, Long providerId) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId) && provider.id().equals(providerId))
                    .findFirst();
        }

        @Override
        public Optional<ModelProvider> findByName(Long tenantId, String providerName) {
            return Optional.empty();
        }

        @Override
        public List<ModelProvider> listByTenant(Long tenantId) {
            return providers;
        }

        @Override
        public List<ConfiguredModel> listModels(Long tenantId, Long providerId) {
            return findById(tenantId, providerId).map(ModelProvider::models).orElse(List.of());
        }

        @Override
        public Optional<ConfiguredModel> findModel(Long tenantId, Long modelId) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId))
                    .flatMap(provider -> provider.models().stream())
                    .filter(model -> model.id().equals(modelId))
                    .findFirst();
        }
    }

    private static final class InMemoryModelInvocationRepository implements ModelInvocationRepository {
        private final List<ModelInvocation> invocations = new ArrayList<>();

        @Override
        public void append(ModelInvocation invocation) {
            invocations.add(invocation);
        }
    }
}
