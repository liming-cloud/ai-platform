package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * 平台代管场景下为租户内已有用户分配租户管理员角色。
 */
public record AssignIamTenantAdministratorCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String userId,
        String roleId
) {
    public AssignIamTenantAdministratorCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        userId = UpdateIamMenuCommand.requireText(userId, "用户 ID");
        roleId = UpdateIamMenuCommand.requireText(roleId, "角色 ID");
    }
}
