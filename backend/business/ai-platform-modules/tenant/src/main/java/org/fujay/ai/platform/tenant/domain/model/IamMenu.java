package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.Entity;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record IamMenu(
        Long id,
        Long tenantId,
        String menuCode,
        String menuName,
        String parentCode,
        String routePath,
        String componentKey,
        String platformScope,
        Integer sortOrder,
        String status) implements Entity {

    public IamMenu {
        requireId(id, "菜单标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        TenantApplicationAccessSpec.requireNotBlank(menuCode, "菜单编码不能为空");
        TenantApplicationAccessSpec.requireNotBlank(menuName, "菜单名称不能为空");
        TenantApplicationAccessSpec.requireNotBlank(routePath, "菜单路由不能为空");
        TenantApplicationAccessSpec.requireNotBlank(componentKey, "菜单组件不能为空");
        menuCode = menuCode.trim();
        menuName = menuName.trim();
        parentCode = TenantApplicationAccessSpec.normalizeOptional(parentCode);
        routePath = routePath.trim();
        componentKey = componentKey.trim();
        platformScope = TenantApplicationAccessSpec.normalizePlatformScope(platformScope);
        status = TenantApplicationAccessSpec.normalizeStatus(status);
        sortOrder = sortOrder == null ? 0 : sortOrder;
        if (menuCode.equals(parentCode)) {
            throw new IllegalArgumentException("菜单父级不能指向自身");
        }
    }

    public IamMenu changeDefinition(
            String newMenuName,
            String newParentCode,
            String newRoutePath,
            String newComponentKey,
            String newPlatformScope,
            Integer newSortOrder,
            String newStatus) {
        return new IamMenu(
                id,
                tenantId,
                menuCode,
                newMenuName,
                newParentCode,
                newRoutePath,
                newComponentKey,
                newPlatformScope,
                newSortOrder,
                newStatus);
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
