package com.aiplatform.identity.application.result;

import java.util.List;

/**
 * IAM 菜单配置读取结果，承载菜单事实、绑定功能和当前租户功能启用状态。
 */
public record IamMenuConfigurationResult(
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
}
