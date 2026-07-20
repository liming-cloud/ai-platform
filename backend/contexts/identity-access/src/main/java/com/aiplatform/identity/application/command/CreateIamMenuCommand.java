package com.aiplatform.identity.application.command;

import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * IAM 菜单创建命令，承载新增菜单事实及其可选租户功能绑定。
 */
public record CreateIamMenuCommand(
        TenantId tenantId,
        String actorId,
        String traceId,
        String code,
        String name,
        String scope,
        String path,
        String routeName,
        String componentKey,
        String icon,
        String requiredPermissionCode,
        int sort,
        String status,
        List<String> featureCodes
) {
    public CreateIamMenuCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        actorId = UpdateIamMenuCommand.requireText(actorId, "操作者 ID");
        traceId = UpdateIamMenuCommand.requireText(traceId, "链路追踪 ID");
        code = UpdateIamMenuCommand.requireText(code, "菜单编码");
        name = UpdateIamMenuCommand.requireText(name, "菜单名称");
        scope = UpdateIamMenuCommand.requireText(scope, "菜单范围");
        if (!"ADMIN".equals(scope) && !"TENANT".equals(scope) && !"BUSINESS".equals(scope)) {
            throw new IllegalArgumentException("菜单范围只允许 ADMIN、TENANT 或 BUSINESS");
        }
        path = UpdateIamMenuCommand.requireText(path, "菜单路径");
        routeName = UpdateIamMenuCommand.requireText(routeName, "路由名称");
        componentKey = UpdateIamMenuCommand.requireText(componentKey, "组件键");
        icon = icon == null ? null : icon.trim();
        requiredPermissionCode = UpdateIamMenuCommand.requireText(requiredPermissionCode, "菜单权限编码");
        status = UpdateIamMenuCommand.requireText(status, "菜单状态");
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("菜单状态只允许 ACTIVE 或 DISABLED");
        }
        featureCodes = featureCodes == null ? List.of() : featureCodes.stream()
                .map(featureCode -> UpdateIamMenuCommand.requireText(featureCode, "租户功能编码"))
                .distinct()
                .sorted()
                .toList();
    }
}
