package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * IAM 角色权限矩阵替换命令，限定在当前租户角色和可分配权限集合内执行。
 */
public record ReplaceIamRolePermissionsCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String roleId,
        List<String> permissionCodes,
        String reason
) {
    public ReplaceIamRolePermissionsCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        roleId = UpdateIamMenuCommand.requireText(roleId, "角色 ID");
        permissionCodes = ReplaceIamMenuFeaturesCommand.normalize(permissionCodes, "权限编码");
        reason = IamChangeReason.normalize(reason, "权限替换原因");
    }
}
