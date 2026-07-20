package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import java.util.Collection;
import java.util.List;

public record IamMenuConfigurationResponse(
        String menuId,
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
        boolean systemBuiltin,
        List<String> featureCodes,
        boolean tenantFeatureEnabled
) {
    public IamMenuConfigurationResponse {
        menuId = IdentityResponseContracts.requireText(menuId, "菜单 ID");
        code = IdentityResponseContracts.requireText(code, "菜单编码");
        name = IdentityResponseContracts.requireText(name, "菜单名称");
        scope = IdentityResponseContracts.requireText(scope, "菜单范围");
        path = IdentityResponseContracts.requireText(path, "菜单路径");
        routeName = IdentityResponseContracts.requireText(routeName, "路由名称");
        componentKey = IdentityResponseContracts.requireText(componentKey, "组件键");
        icon = icon == null ? null : icon.trim();
        requiredPermissionCode = IdentityResponseContracts.requireText(requiredPermissionCode, "菜单权限编码");
        status = IdentityResponseContracts.requireText(status, "菜单状态");
        featureCodes = normalizeOptionalTextList(featureCodes, "功能编码");
    }

    public static IamMenuConfigurationResponse from(IamMenuConfigurationResult result) {
        IdentityResponseContracts.requireValue(result, "IAM 菜单配置结果");
        return new IamMenuConfigurationResponse(
                result.menuId(),
                result.code(),
                result.name(),
                result.scope(),
                result.path(),
                result.routeName(),
                result.componentKey(),
                result.icon(),
                result.requiredPermissionCode(),
                result.sort(),
                result.status(),
                result.systemBuiltin(),
                result.featureCodes(),
                result.tenantFeatureEnabled()
        );
    }

    private static List<String> normalizeOptionalTextList(Collection<String> values, String fieldName) {
        IdentityResponseContracts.requireValue(values, fieldName + "列表");
        return values.stream()
                .map(value -> IdentityResponseContracts.requireText(value, fieldName))
                .distinct()
                .sorted()
                .toList();
    }
}
