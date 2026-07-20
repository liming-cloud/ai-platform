package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * IAM 角色默认首页更新命令，确保默认菜单来自当前角色有效菜单集合。
 */
public record UpdateIamRoleDefaultMenuCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String roleId,
        String defaultMenuCode
) {
    public UpdateIamRoleDefaultMenuCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        roleId = UpdateIamMenuCommand.requireText(roleId, "角色 ID");
        defaultMenuCode = UpdateIamMenuCommand.requireText(defaultMenuCode, "默认菜单编码");
    }
}
