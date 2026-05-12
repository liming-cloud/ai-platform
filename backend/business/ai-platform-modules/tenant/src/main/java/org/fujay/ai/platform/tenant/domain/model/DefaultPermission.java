package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.ValueObject;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record DefaultPermission(String permissionCode, String permissionName) implements ValueObject {

    public DefaultPermission {
        permissionCode = TenantApplicationAccessSpec.normalizeRequired(permissionCode, "默认权限编码不能为空");
        permissionName = TenantApplicationAccessSpec.normalizeRequired(permissionName, "默认权限名称不能为空");
    }
}
