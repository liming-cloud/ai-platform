package com.aiplatform.modelhub.application.result;

import com.aiplatform.modelhub.domain.ModelProvider;

/**
 * 模型供应商详情结果，供控制台从持久化数据恢复列表。
 */
public record ModelProviderDetailResult(
        String providerId,
        String providerCode,
        String providerName,
        String endpoint,
        String status,
        String approvalRequestId,
        int version
) {
    public ModelProviderDetailResult {
        providerId = ModelHubResultContracts.requireText(providerId, "供应商 ID");
        providerCode = ModelHubResultContracts.requireText(providerCode, "供应商编码");
        providerName = ModelHubResultContracts.requireText(providerName, "供应商名称");
        endpoint = ModelHubResultContracts.requireText(endpoint, "供应商 Endpoint");
        status = ModelHubResultContracts.requireText(status, "供应商状态");
        approvalRequestId = ModelHubResultContracts.normalizeApprovalRequestId(status, approvalRequestId);
        if (version < 0) {
            throw new IllegalArgumentException("模型供应商版本不能小于 0");
        }
    }

    public static ModelProviderDetailResult from(ModelProvider provider) {
        return new ModelProviderDetailResult(
                provider.providerId(),
                provider.providerCode(),
                provider.providerName(),
                provider.endpoint(),
                provider.status().name(),
                provider.approvalRequestId(),
                provider.version()
        );
    }
}
