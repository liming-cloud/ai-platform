package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.Entity;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record UserCredential(Long id, Long tenantId, Long userId, String passwordHash) implements Entity {

    public UserCredential {
        requireId(id, "凭证标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        requireId(userId, "用户标识不能为空");
        TenantApplicationAccessSpec.requireNotBlank(passwordHash, "密码哈希不能为空");
        passwordHash = passwordHash.trim();
    }

    public UserCredential changePasswordHash(String newPasswordHash) {
        return new UserCredential(id, tenantId, userId, newPasswordHash);
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
