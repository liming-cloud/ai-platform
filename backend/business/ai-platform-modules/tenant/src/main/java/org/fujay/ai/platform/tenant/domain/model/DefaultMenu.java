package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.ValueObject;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record DefaultMenu(
        String menuCode,
        String menuName,
        String parentCode,
        String routePath,
        String componentKey,
        String platformScope,
        Integer sortOrder) implements ValueObject {

    public DefaultMenu {
        menuCode = TenantApplicationAccessSpec.normalizeRequired(menuCode, "默认菜单编码不能为空");
        menuName = TenantApplicationAccessSpec.normalizeRequired(menuName, "默认菜单名称不能为空");
        parentCode = TenantApplicationAccessSpec.normalizeOptional(parentCode);
        routePath = TenantApplicationAccessSpec.normalizeRequired(routePath, "默认菜单路由不能为空");
        componentKey = TenantApplicationAccessSpec.normalizeRequired(componentKey, "默认菜单组件不能为空");
        platformScope = TenantApplicationAccessSpec.normalizePlatformScope(platformScope);
        sortOrder = sortOrder == null ? 0 : sortOrder;
        if (menuCode.equals(parentCode)) {
            throw new IllegalArgumentException("默认菜单父级不能指向自身");
        }
    }
}
