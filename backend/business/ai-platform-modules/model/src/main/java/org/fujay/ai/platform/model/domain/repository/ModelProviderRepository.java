package org.fujay.ai.platform.model.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;

public interface ModelProviderRepository {

    void saveProvider(ProviderRecord provider, List<ModelRecord> models);

    Optional<ProviderRecord> findProvider(Long tenantId, Long providerId);

    Optional<ProviderRecord> findProviderByName(Long tenantId, String providerName);

    List<ProviderRecord> listProviders(Long tenantId);

    List<ProviderTypeOptionRecord> listProviderTypeOptions();

    List<ModelOptionRecord> listModelOptions();

    List<ProviderTypeOptionRecord> listAllProviderTypeOptions();

    List<ModelOptionRecord> listAllModelOptions();

    void saveProviderTypeOption(ProviderTypeOptionRecord option);

    void saveModelOption(ModelOptionRecord option);

    List<ModelRecord> listModels(Long tenantId, Long providerId);

    Optional<ModelRecord> findModel(Long tenantId, Long modelId);

    void appendInvocation(InvocationRecord invocation);

    record ProviderRecord(
            Long id,
            Long tenantId,
            String providerName,
            ProviderType providerType,
            String baseUrl,
            String apiKeyCiphertext,
            String apiKeyLast4,
            String status,
            BigDecimal budgetUsd,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    record ProviderTypeOptionRecord(
            Long id,
            ProviderType providerType,
            String displayName,
            String defaultBaseUrl,
            String status,
            Integer sortOrder) {
    }

    record ModelOptionRecord(
            Long id,
            Long providerTypeId,
            String modelCode,
            String displayName,
            ModelCapabilityType capabilityType,
            ModelParameterDefaults defaults,
            String status,
            Integer sortOrder) {
    }

    record ModelRecord(
            Long id,
            Long tenantId,
            Long providerId,
            String modelCode,
            String displayName,
            ModelCapabilityType capabilityType,
            String status,
            ModelParameterDefaults defaults,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    record InvocationRecord(
            Long id,
            Long tenantId,
            Long providerId,
            Long modelId,
            ProviderType providerType,
            String modelCode,
            ModelCapabilityType capabilityType,
            String status,
            long latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            String errorCode,
            String errorMessage,
            LocalDateTime createdAt) {
    }
}
