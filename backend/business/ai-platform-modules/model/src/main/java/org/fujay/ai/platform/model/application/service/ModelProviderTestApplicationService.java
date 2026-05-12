package org.fujay.ai.platform.model.application.service;

import org.fujay.ai.platform.model.application.command.TestModelCommand;
import org.fujay.ai.platform.model.application.dto.ModelTestDto;
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
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelProviderTestApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelProviderTestApplicationService.class);
    private static final String INVOCATION_KIND_PROVIDER_TEST = "PROVIDER_TEST";
    private static final String PROVIDER_TEST_COST_MISSING_REASON = "连接测试不计算成本";

    private final ModelProviderRepository providerRepository;
    private final ModelInvocationRepository invocationRepository;
    private final CurrentPrincipalPort currentPrincipalPort;
    private final ModelGatewayPort modelGatewayPort;
    private final DomainIdGeneratorPort idGenerator;

    public ModelProviderTestApplicationService(
            ModelProviderRepository providerRepository,
            ModelInvocationRepository invocationRepository,
            CurrentPrincipalPort currentPrincipalPort,
            ModelGatewayPort modelGatewayPort,
            DomainIdGeneratorPort idGenerator) {
        this.providerRepository = providerRepository;
        this.invocationRepository = invocationRepository;
        this.currentPrincipalPort = currentPrincipalPort;
        this.modelGatewayPort = modelGatewayPort;
        this.idGenerator = idGenerator;
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public ModelTestDto testModel(TestModelCommand command) {
        Long tenantId = currentPrincipalPort.currentTenantId();
        return TenantContext.callWith(new TenantId(tenantId), () -> doTestModel(tenantId, command));
    }

    private ModelTestDto doTestModel(Long tenantId, TestModelCommand command) {
        ConfiguredModel selectedModel = providerRepository
                .findModel(tenantId, command.modelId())
                .orElseThrow(() -> new PlatformException(ModelErrorCode.MODEL_NOT_FOUND));
        ModelProvider provider = providerRepository
                .findById(tenantId, selectedModel.providerId())
                .orElseThrow(() -> new PlatformException(ModelErrorCode.PROVIDER_NOT_FOUND));
        ConfiguredModel model = provider.requireChatModel(selectedModel.id());
        String prompt = normalizeProviderTestPrompt(command.prompt());
        long startedAt = System.nanoTime();
        try {
            ModelGatewayChatResult result = modelGatewayPort.testChat(toGatewayRequest(provider, model, prompt));
            long latencyMs = elapsedMs(startedAt);
            invocationRepository.append(ModelInvocation.success(
                    idGenerator.nextId(), provider, model, INVOCATION_KIND_PROVIDER_TEST, TraceContext.currentTraceId(),
                    latencyMs, result.promptTokens(), result.completionTokens(), result.totalTokens(), prompt.length(),
                    safeLength(result.output()), null, PROVIDER_TEST_COST_MISSING_REASON));
            return new ModelTestDto(true, provider.id(), model.id(), provider.providerName(), model.modelCode(),
                    model.capabilityType(), latencyMs, result.output());
        } catch (RuntimeException ex) {
            long latencyMs = elapsedMs(startedAt);
            LOGGER.warn("模型供应商连接测试失败：{}", ex.getMessage());
            invocationRepository.append(ModelInvocation.failed(
                    idGenerator.nextId(), provider, model, INVOCATION_KIND_PROVIDER_TEST, TraceContext.currentTraceId(),
                    latencyMs, prompt.length(), PROVIDER_TEST_COST_MISSING_REASON, ModelErrorCode.PROVIDER_TEST_FAILED, ex));
            throw new PlatformException(ModelErrorCode.PROVIDER_TEST_FAILED);
        }
    }

    private ModelGatewayChatRequest toGatewayRequest(ModelProvider provider, ConfiguredModel model, String prompt) {
        return new ModelGatewayChatRequest(provider.providerType(), provider.baseUrl(), provider.apiKeyCiphertext(),
                model.modelCode(), prompt, model.defaults().maxOutputTokens(), model.defaults().temperature(),
                model.defaults().topP(), model.defaults().frequencyPenalty(), model.defaults().presencePenalty(),
                model.defaults().reasoningEffort());
    }

    private String normalizeProviderTestPrompt(String prompt) {
        return prompt == null || prompt.isBlank() ? "请用一句话回复：AI Platform provider test ok" : prompt;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
