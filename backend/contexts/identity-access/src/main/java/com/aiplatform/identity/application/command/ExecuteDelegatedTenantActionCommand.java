package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * 平台管理员代管租户动作应用命令，区分操作者租户和被代管租户。
 */
public record ExecuteDelegatedTenantActionCommand(
        TenantId operatorTenantId,
        TenantId targetTenantId,
        String operatorId,
        String traceId,
        String actionType,
        String targetRoleId,
        String targetUserId,
        List<String> permissionCodes,
        String reason,
        String idempotencyKey
) {
    public ExecuteDelegatedTenantActionCommand {
        Objects.requireNonNull(operatorTenantId, "操作者租户 ID 不能为空");
        Objects.requireNonNull(targetTenantId, "被代管租户 ID 不能为空");
        operatorId = UpdateIamMenuCommand.requireText(operatorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
    }
}
