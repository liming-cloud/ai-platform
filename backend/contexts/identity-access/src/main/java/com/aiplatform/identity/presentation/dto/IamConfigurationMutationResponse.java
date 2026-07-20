package com.aiplatform.identity.presentation.dto;

/**
 * IAM 配置保存响应 DTO，返回最小资源标识和保存状态，避免回传过量授权事实。
 */
public record IamConfigurationMutationResponse(
        String resourceId,
        String status
) {
    public IamConfigurationMutationResponse {
        resourceId = IdentityResponseContracts.requireText(resourceId, "资源 ID");
        status = IdentityResponseContracts.requireText(status, "保存状态");
    }

    public static IamConfigurationMutationResponse saved(String resourceId) {
        return new IamConfigurationMutationResponse(resourceId, "SAVED");
    }
}
