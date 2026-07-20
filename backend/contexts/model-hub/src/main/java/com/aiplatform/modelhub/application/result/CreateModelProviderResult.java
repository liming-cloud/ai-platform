package com.aiplatform.modelhub.application.result;

/**
 * 模型供应商创建结果，返回供应商 ID、审批状态和审批请求引用。
 */
public record CreateModelProviderResult(
        String providerId,
        String status,
        String approvalRequestId
) {
    public CreateModelProviderResult {
        providerId = ModelHubResultContracts.requireText(providerId, "供应商 ID");
        status = ModelHubResultContracts.requireText(status, "供应商状态");
        approvalRequestId = ModelHubResultContracts.requireText(approvalRequestId, "审批请求 ID");
    }
}
