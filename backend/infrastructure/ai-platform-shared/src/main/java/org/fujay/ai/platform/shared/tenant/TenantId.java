package org.fujay.ai.platform.shared.tenant;

import java.util.Objects;

public record TenantId(Long value) {

    public TenantId {
        Objects.requireNonNull(value, "租户标识不能为空");
        if (value <= 0) {
            throw new IllegalArgumentException("租户标识必须是正数");
        }
    }
}
