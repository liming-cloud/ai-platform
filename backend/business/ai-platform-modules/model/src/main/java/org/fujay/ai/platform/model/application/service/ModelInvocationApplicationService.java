package org.fujay.ai.platform.model.application.service;

import org.fujay.ai.platform.model.application.command.ChatInvocationCommand;
import org.fujay.ai.platform.model.application.dto.ChatInvocationDto;
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
public class ModelInvocationApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelInvocationApplicationService.class);
    private static final String INVOCATION_KIND_CHAT = "CHAT";
    private static final String COST_MISSING_REASON = "模型计费单价未配置";
    private static final String USAGE_MISSING_REASON = "供应商未返回 token usage";

    private final ModelProviderRepository providerRepository;
    private final ModelInvocationRepository invocationRepository;
    private final CurrentPrincipalPort currentPrincipalPort;
    private final ModelGatewayPort modelGatewayPort;
    private final DomainIdGeneratorPort idGenerator;

    public ModelInvocationApplicationService(
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
    public ChatInvocationDto invokeChat(ChatInvocationCommand command) {
        Long tenantId = currentPrincipalPort.currentTenantId();
        return TenantContext.callWith(new TenantId(tenantId), () -> doInvokeChat(tenantId, command));
    }

    private ChatInvocationDto doInvokeChat(Long tenantId, ChatInvocationCommand command) {
        Long modelId = parseModelId(command.modelId());
        String prompt = normalizePrompt(command.prompt());
        ConfiguredModel selectedModel = providerRepository
                .findModel(tenantId, modelId)
                .orElseThrow(() -> new PlatformException(ModelErrorCode.MODEL_NOT_FOUND));
        ModelProvider provider = providerRepository
                .findById(tenantId, selectedModel.providerId())
                .orElseThrow(() -> new PlatformException(ModelErrorCode.PROVIDER_NOT_FOUND));
        ConfiguredModel model = provider.requireChatModel(selectedModel.id());

        String traceId = TraceContext.currentTraceId();
        long startedAt = System.nanoTime();
        try {
            ModelGatewayChatResult result = modelGatewayPort.invokeChat(toGatewayRequest(provider, model, prompt));
            long latencyMs = elapsedMs(startedAt);
            invocationRepository.append(ModelInvocation.success(
                    idGenerator.nextId(),
                    provider,
                    model,
                    INVOCATION_KIND_CHAT,
                    traceId,
                    latencyMs,
                    result.promptTokens(),
                    result.completionTokens(),
                    result.totalTokens(),
                    prompt.length(),
                    safeLength(result.output()),
                    null,
                    COST_MISSING_REASON));
            return new ChatInvocationDto(
                    true,
                    provider.id(),
                    model.id(),
                    provider.providerName(),
                    model.modelCode(),
                    model.displayName(),
                    model.capabilityType(),
                    traceId,
                    latencyMs,
                    result.output(),
                    result.promptTokens(),
                    result.completionTokens(),
                    result.totalTokens(),
                    usageMissingReason(result),
                    null,
                    COST_MISSING_REASON);
        } catch (RuntimeException ex) {
            long latencyMs = elapsedMs(startedAt);
            LOGGER.warn("模型统一调用失败：{}", ex.getMessage());
            invocationRepository.append(ModelInvocation.failed(
                    idGenerator.nextId(),
                    provider,
                    model,
                    INVOCATION_KIND_CHAT,
                    traceId,
                    latencyMs,
                    prompt.length(),
                    COST_MISSING_REASON,
                    ModelErrorCode.MODEL_INVOCATION_FAILED,
                    ex));
            throw new PlatformException(ModelErrorCode.MODEL_INVOCATION_FAILED);
        }
    }

    private ModelGatewayChatRequest toGatewayRequest(ModelProvider provider, ConfiguredModel model, String prompt) {
        return new ModelGatewayChatRequest(provider.providerType(), provider.baseUrl(), provider.apiKeyCiphertext(),
                model.modelCode(), prompt, model.defaults().maxOutputTokens(), model.defaults().temperature(),
                model.defaults().topP(), model.defaults().frequencyPenalty(), model.defaults().presencePenalty(),
                model.defaults().reasoningEffort());
    }

    private String usageMissingReason(ModelGatewayChatResult result) {
        if (result.promptTokens() == null && result.completionTokens() == null && result.totalTokens() == null) {
            return USAGE_MISSING_REASON;
        }
        return null;
    }

    private Long parseModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型标识不能为空");
        }
        return Long.parseLong(modelId);
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("调用 prompt 不能为空");
        }
        return prompt.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
