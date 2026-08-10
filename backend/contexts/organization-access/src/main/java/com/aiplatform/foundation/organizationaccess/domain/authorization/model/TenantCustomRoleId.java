package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 租户自定义角色的稳定标识。 */
public record TenantCustomRoleId(long value) implements ValueObject {

    public TenantCustomRoleId {
        if (value <= 0L) {
            throw new IllegalArgumentException("租户自定义角色标识必须为正数");
        }
    }
}
