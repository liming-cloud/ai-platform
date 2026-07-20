package com.aiplatform.modelhub.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 停用模型配置命令，危险操作必须携带服务端租户、操作者和路径中的模型配置标识。
 */
public record DisableModelConfigCommand(
        TenantId tenantId,
        String operatorId,
        String providerId,
        String modelConfigId,
        String reason,
        int expectedVersion
) {
    public DisableModelConfigCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作者 ID");
        providerId = requireText(providerId, "模型供应商 ID");
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        reason = DisableReasonPolicy.normalize(reason);
        requireVersion(expectedVersion);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireVersion(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("预期版本不能小于 0");
        }
    }
}
