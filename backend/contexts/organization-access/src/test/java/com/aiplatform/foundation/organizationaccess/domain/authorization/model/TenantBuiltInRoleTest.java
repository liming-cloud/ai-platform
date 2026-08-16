package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantBuiltInRoleTest {

    @Test
    void 产品只发布租户管理员和普通用户两种租户角色() {
        assertIterableEquals(
                java.util.List.of("TENANT_ADMINISTRATOR", "TENANT_USER"),
                java.util.Arrays.stream(TenantBuiltInRole.values())
                        .map(Enum::name)
                        .toList());

        assertEquals(Set.of(
                new PermissionCode("access:manage"),
                new PermissionCode("knowledge:read"),
                new PermissionCode("knowledge:write"),
                new PermissionCode("model:read"),
                new PermissionCode("model:write"),
                new PermissionCode("assistant:read"),
                new PermissionCode("assistant:write"),
                new PermissionCode("usage:read"),
                new PermissionCode("audit:read"),
                new PermissionCode("assistant:query")),
                TenantBuiltInRole.TENANT_ADMINISTRATOR.permissions());
        assertEquals(Set.of(
                new PermissionCode("assistant:query"),
                new PermissionCode("knowledge:read")),
                TenantBuiltInRole.TENANT_USER.permissions());
    }
}
