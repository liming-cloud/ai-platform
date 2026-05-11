package org.fujay.ai.platform.security;

import java.util.Objects;
import org.fujay.ai.platform.shared.tenant.TenantId;

public record AuthPrincipal(TenantId tenantId, String actorType, Long actorId) {

    public AuthPrincipal {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(actorType, "认证主体类型不能为空");
        Objects.requireNonNull(actorId, "认证主体标识不能为空");
        if (actorType.isBlank()) {
            throw new IllegalArgumentException("认证主体类型不能为空白");
        }
        if (actorId <= 0) {
            throw new IllegalArgumentException("认证主体标识必须是正数");
        }
    }
}
