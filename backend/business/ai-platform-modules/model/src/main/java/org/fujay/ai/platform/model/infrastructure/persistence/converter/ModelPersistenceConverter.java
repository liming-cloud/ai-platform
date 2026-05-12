package org.fujay.ai.platform.model.infrastructure.persistence.converter;

import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelInvocation;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.aggregate.PlatformModelOption;
import org.fujay.ai.platform.model.domain.aggregate.PlatformProviderTypeOption;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelCatalogPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelInvocationRecordPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelProviderPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformModelOptionPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformProviderTypePO;

public final class ModelPersistenceConverter {

    private ModelPersistenceConverter() {
    }

    public static ModelProviderPO toProviderPO(ModelProvider provider) {
        ModelProviderPO po = new ModelProviderPO();
        po.setId(provider.id());
        po.setTenantId(provider.tenantId());
        po.setProviderName(provider.providerName());
        po.setProviderType(provider.providerType().name());
        po.setBaseUrl(provider.baseUrl());
        po.setApiKeyCiphertext(provider.apiKeyCiphertext());
        po.setApiKeyLast4(provider.apiKeyLast4());
        po.setStatus(provider.status());
        po.setBudgetUsd(provider.budgetUsd());
        po.setCreatedAt(provider.createdAt());
        po.setUpdatedAt(provider.updatedAt());
        return po;
    }

    public static ModelProvider toProvider(ModelProviderPO po, java.util.List<ConfiguredModel> models) {
        return new ModelProvider(
                po.getId(),
                po.getTenantId(),
                po.getProviderName(),
                ProviderType.valueOf(po.getProviderType()),
                po.getBaseUrl(),
                po.getApiKeyCiphertext(),
                po.getApiKeyLast4(),
                po.getStatus(),
                po.getBudgetUsd(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                models);
    }

    public static ModelCatalogPO toModelPO(ConfiguredModel model) {
        ModelParameterDefaults defaults = model.defaults();
        ModelCatalogPO po = new ModelCatalogPO();
        po.setId(model.id());
        po.setTenantId(model.tenantId());
        po.setProviderId(model.providerId());
        po.setModelCode(model.modelCode());
        po.setDisplayName(model.displayName());
        po.setCapabilityType(model.capabilityType().name());
        po.setStatus(model.status());
        po.setContextWindow(defaults.contextWindow());
        po.setMaxOutputTokens(defaults.maxOutputTokens());
        po.setEmbeddingDimensions(defaults.embeddingDimensions());
        po.setDefaultTemperature(defaults.temperature());
        po.setDefaultTopP(defaults.topP());
        po.setDefaultTopK(defaults.topK());
        po.setDefaultFrequencyPenalty(defaults.frequencyPenalty());
        po.setDefaultPresencePenalty(defaults.presencePenalty());
        po.setDefaultReasoningEffort(defaults.reasoningEffort());
        po.setCreatedAt(model.createdAt());
        po.setUpdatedAt(model.updatedAt());
        return po;
    }

    public static ConfiguredModel toModel(ModelCatalogPO po) {
        return new ConfiguredModel(
                po.getId(),
                po.getTenantId(),
                po.getProviderId(),
                po.getModelCode(),
                po.getDisplayName(),
                ModelCapabilityType.valueOf(po.getCapabilityType()),
                po.getStatus(),
                defaults(
                        po.getContextWindow(),
                        po.getMaxOutputTokens(),
                        po.getEmbeddingDimensions(),
                        po.getDefaultTemperature(),
                        po.getDefaultTopP(),
                        po.getDefaultTopK(),
                        po.getDefaultFrequencyPenalty(),
                        po.getDefaultPresencePenalty(),
                        po.getDefaultReasoningEffort()),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }

    public static PlatformProviderTypePO toProviderTypePO(PlatformProviderTypeOption option) {
        PlatformProviderTypePO po = new PlatformProviderTypePO();
        po.setId(option.id());
        po.setProviderType(option.providerType().name());
        po.setDisplayName(option.displayName());
        po.setDefaultBaseUrl(option.defaultBaseUrl());
        po.setStatus(option.status());
        po.setSortOrder(option.sortOrder());
        return po;
    }

    public static PlatformProviderTypeOption toProviderTypeOption(PlatformProviderTypePO po) {
        return new PlatformProviderTypeOption(
                po.getId(),
                ProviderType.valueOf(po.getProviderType()),
                po.getDisplayName(),
                po.getDefaultBaseUrl(),
                po.getStatus(),
                po.getSortOrder());
    }

    public static PlatformModelOptionPO toPlatformModelOptionPO(PlatformModelOption option) {
        ModelParameterDefaults defaults = option.defaults();
        PlatformModelOptionPO po = new PlatformModelOptionPO();
        po.setId(option.id());
        po.setProviderTypeId(option.providerTypeId());
        po.setModelCode(option.modelCode());
        po.setDisplayName(option.displayName());
        po.setCapabilityType(option.capabilityType().name());
        po.setContextWindow(defaults.contextWindow());
        po.setMaxOutputTokens(defaults.maxOutputTokens());
        po.setEmbeddingDimensions(defaults.embeddingDimensions());
        po.setDefaultTemperature(defaults.temperature());
        po.setDefaultTopP(defaults.topP());
        po.setDefaultTopK(defaults.topK());
        po.setDefaultFrequencyPenalty(defaults.frequencyPenalty());
        po.setDefaultPresencePenalty(defaults.presencePenalty());
        po.setDefaultReasoningEffort(defaults.reasoningEffort());
        po.setStatus(option.status());
        po.setSortOrder(option.sortOrder());
        return po;
    }

    public static PlatformModelOption toModelOption(PlatformModelOptionPO po) {
        return new PlatformModelOption(
                po.getId(),
                po.getProviderTypeId(),
                po.getModelCode(),
                po.getDisplayName(),
                ModelCapabilityType.valueOf(po.getCapabilityType()),
                defaults(
                        po.getContextWindow(),
                        po.getMaxOutputTokens(),
                        po.getEmbeddingDimensions(),
                        po.getDefaultTemperature(),
                        po.getDefaultTopP(),
                        po.getDefaultTopK(),
                        po.getDefaultFrequencyPenalty(),
                        po.getDefaultPresencePenalty(),
                        po.getDefaultReasoningEffort()),
                po.getStatus(),
                po.getSortOrder());
    }

    public static ModelInvocationRecordPO toInvocationPO(ModelInvocation invocation) {
        ModelInvocationRecordPO po = new ModelInvocationRecordPO();
        po.setId(invocation.id());
        po.setTenantId(invocation.tenantId());
        po.setProviderId(invocation.providerId());
        po.setModelId(invocation.modelId());
        po.setProviderType(invocation.providerType().name());
        po.setModelCode(invocation.modelCode());
        po.setCapabilityType(invocation.capabilityType().name());
        po.setInvocationKind(invocation.invocationKind());
        po.setTraceId(invocation.traceId());
        po.setStatus(invocation.status());
        po.setLatencyMs(invocation.latencyMs());
        po.setPromptTokens(invocation.promptTokens());
        po.setCompletionTokens(invocation.completionTokens());
        po.setTotalTokens(invocation.totalTokens());
        po.setInputChars(invocation.inputChars());
        po.setOutputChars(invocation.outputChars());
        po.setCostUsd(invocation.costUsd());
        po.setCostMissingReason(invocation.costMissingReason());
        po.setErrorCode(invocation.errorCode());
        po.setErrorMessage(invocation.errorMessage());
        po.setCreatedAt(invocation.createdAt());
        return po;
    }

    private static ModelParameterDefaults defaults(
            Integer contextWindow,
            Integer maxOutputTokens,
            Integer embeddingDimensions,
            java.math.BigDecimal temperature,
            java.math.BigDecimal topP,
            Integer topK,
            java.math.BigDecimal frequencyPenalty,
            java.math.BigDecimal presencePenalty,
            String reasoningEffort) {
        return new ModelParameterDefaults(contextWindow, maxOutputTokens, embeddingDimensions, temperature, topP, topK,
                frequencyPenalty, presencePenalty, reasoningEffort);
    }
}
