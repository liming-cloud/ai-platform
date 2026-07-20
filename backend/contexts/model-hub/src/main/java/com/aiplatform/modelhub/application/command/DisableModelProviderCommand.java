package com.aiplatform.modelhub.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 停用模型供应商命令，入口层必须使用服务端租户、操作者和路径中的供应商标识。
 */
public record DisableModelProviderCommand(
        TenantId tenantId,
        String operatorId,
        String providerId,
        String reason,
        int expectedVersion
) {
    public DisableModelProviderCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
        providerId = requireText(providerId, "模型供应商 ID");
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
