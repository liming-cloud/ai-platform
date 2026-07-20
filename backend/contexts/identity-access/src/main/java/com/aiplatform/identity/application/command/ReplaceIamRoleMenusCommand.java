package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * IAM 角色菜单矩阵替换命令，限定在当前租户角色和可分配菜单集合内执行。
 */
public record ReplaceIamRoleMenusCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String roleId,
        List<String> menuIds
) {
    public ReplaceIamRoleMenusCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        roleId = UpdateIamMenuCommand.requireText(roleId, "角色 ID");
        menuIds = ReplaceIamMenuFeaturesCommand.normalize(menuIds, "菜单 ID");
    }
}
