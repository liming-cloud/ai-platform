package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;

/**
 * IAM 菜单更新命令，承载平台菜单事实保存所需的服务端上下文和请求字段。
 */
public record UpdateIamMenuCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String menuId,
        String name,
        String path,
        String routeName,
        String componentKey,
        String icon,
        String requiredPermissionCode,
        int sort,
        String status
) {
    public UpdateIamMenuCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = requireText(actorId, "操作者 ID");
        traceId = requireText(traceId, "链路追踪 ID");
        menuId = requireText(menuId, "菜单 ID");
        name = requireText(name, "菜单名称");
        path = requireText(path, "菜单路径");
        routeName = requireText(routeName, "路由名称");
        componentKey = requireText(componentKey, "组件键");
        icon = icon == null ? null : icon.trim();
        requiredPermissionCode = requireText(requiredPermissionCode, "菜单权限编码");
        status = requireText(status, "菜单状态");
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("菜单状态只允许 ACTIVE 或 DISABLED");
        }
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
