package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantBuiltInRoleTest {

    @Test
    void 模型管理员和问答用户只得到各自已有授权点() {
        assertEquals(Set.of(
                new PermissionCode("model:read"),
                new PermissionCode("model:write")),
                TenantBuiltInRole.TENANT_MODEL_MANAGER.permissions());
        assertEquals(Set.of(
                new PermissionCode("assistant:query"),
                new PermissionCode("knowledge:read")),
                TenantBuiltInRole.TENANT_QUERY_USER.permissions());
    }
}
