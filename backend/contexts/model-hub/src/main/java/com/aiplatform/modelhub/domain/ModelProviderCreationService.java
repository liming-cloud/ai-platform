package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 模型供应商创建领域服务，集中约束初始状态、认证类型和审批风险等级。
 */
public class ModelProviderCreationService {
    public ModelProviderCreation createProvider(
            TenantId tenantId,
            String providerId,
            String providerType,
            String providerName,
            String apiEndpoint,
            String authType,
            String operatorId,
            String traceId,
            Instant requestedAt
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String normalizedProviderId = requireText(providerId, "模型供应商 ID");
        String normalizedProviderCode = requireText(providerType, "模型供应商类型").toLowerCase(Locale.ROOT);
        String normalizedProviderName = requireText(providerName, "模型供应商名称");
        String normalizedApiEndpoint = requireText(apiEndpoint, "模型供应商地址");
        String normalizedAuthType = ModelProviderAuthTypes.normalize(authType);
        String normalizedOperatorId = requireText(operatorId, "操作人 ID");
        String normalizedTraceId = requireText(traceId, "链路追踪 ID");
        Objects.requireNonNull(requestedAt, "请求时间不能为空");
        String approvalRequestId = UUID.randomUUID().toString();
        ModelProvider provider = new ModelProvider(
                tenantId,
                normalizedProviderId,
                normalizedProviderCode,
                normalizedProviderName,
                normalizedApiEndpoint,
                normalizedAuthType,
                null,
                ModelProviderStatus.PENDING_APPROVAL,
                approvalRequestId,
                true,
                requestedAt,
                requestedAt
        );
        ModelConfigChange change = ModelConfigChange.forProviderCreation(
                provider,
                ModelProviderAuthTypes.requiresCredentialMaterial(provider.authType()),
                normalizedOperatorId,
                normalizedTraceId,
                requestedAt
        );
        provider = provider.withApprovalEvidence(change.approvalEvidence(), change.targetDigest());
        return new ModelProviderCreation(provider, change);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
