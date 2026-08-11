package com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 平台内置角色绑定的稳定标识。 */
public record PlatformRoleBindingId(long value) implements ValueObject {

    public PlatformRoleBindingId {
        if (value <= 0L) {
            throw new IllegalArgumentException("平台角色绑定标识必须为正数");
        }
    }
}
