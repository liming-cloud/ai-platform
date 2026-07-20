package com.aiplatform.appstudio.domain.port.modelpolicy.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Map;
import java.util.Set;

/**
 * AI 应用模型使用策略请求，包含主体、权限快照、应用资源和预估用量。
 */
public record AppModelUsageRequest(
        TenantId tenantId,
        String actorId,
        String actorType,
        Set<String> roleCodes,
        Set<PermissionCode> permissionSnapshot,
        String applicationId,
        String useCase,
        String requiredCapability,
        String requestedModelConfigId,
        Map<String, Object> requestedOptions,
        long estimatedInputTokens,
        long estimatedOutputTokens
) implements ValueObject {
    public AppModelUsageRequest {
        tenantId = AppStudioPortContracts.requireValue(tenantId, "租户 ID");
        actorId = AppStudioPortContracts.requireText(actorId, "操作者 ID");
        actorType = AppStudioPortContracts.requireText(actorType, "操作者类型");
        roleCodes = AppStudioPortContracts.normalizeOptionalTextSet(roleCodes, "角色编码");
        permissionSnapshot = AppStudioPortContracts.copyRequiredSet(permissionSnapshot, "权限快照");
        applicationId = AppStudioPortContracts.requireText(applicationId, "应用 ID");
        useCase = AppStudioPortContracts.requireText(useCase, "模型使用场景");
        requiredCapability = AppStudioPortContracts.requireText(requiredCapability, "模型能力");
        requestedModelConfigId = AppStudioPortContracts.requireText(requestedModelConfigId, "模型配置 ID");
        requestedOptions = AppStudioPortContracts.copyRequiredMap(requestedOptions, "模型 options");
        if (estimatedInputTokens < 0 || estimatedOutputTokens < 0) {
            throw new IllegalArgumentException("预估 Token 不能小于 0");
        }
    }
}
