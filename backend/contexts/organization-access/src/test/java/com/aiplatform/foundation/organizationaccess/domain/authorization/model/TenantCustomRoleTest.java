package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantCustomRoleTest {

    private static final TenantId TENANT = new TenantId(730000000000000001L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T03:00:00Z");

    @Test
    void 只接受已发布且非空的租户权限集合() {
        var role = TenantCustomRole.create(
                new TenantCustomRoleId(730000000000000011L),
                TENANT,
                "知识审校员",
                Set.of(new PermissionCode("knowledge:read"), new PermissionCode("knowledge:write")),
                CREATED_AT);

        assertTrue(role.isActive());
        assertEquals(0L, role.version());
        assertEquals(Set.of(
                new PermissionCode("knowledge:read"),
                new PermissionCode("knowledge:write")), role.permissions());
        assertThrows(IllegalArgumentException.class, () -> TenantCustomRole.create(
                new TenantCustomRoleId(730000000000000012L),
                TENANT,
                "越权角色",
                Set.of(new PermissionCode("tenant-governance:write")),
                CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> TenantCustomRole.create(
                new TenantCustomRoleId(730000000000000013L),
                TENANT,
                "空角色",
                Set.of(),
                CREATED_AT));
    }

    @Test
    void 停用后拒绝更新权限并保留不可变修订事实() {
        var role = TenantCustomRole.create(
                new TenantCustomRoleId(730000000000000014L),
                TENANT,
                "模型审校员",
                Set.of(new PermissionCode("model:read")),
                CREATED_AT);

        var disabled = role.disable(CREATED_AT.plusSeconds(1));

        assertFalse(disabled.isActive());
        assertEquals(1L, disabled.version());
        assertThrows(IllegalStateException.class, () -> disabled.changePermissions(
                Set.of(new PermissionCode("model:write")), CREATED_AT.plusSeconds(2)));
    }
}
