package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformRoleBindingTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void 内置平台角色绑定应从角色模板派生权限并可撤销() {
        var binding = PlatformRoleBinding.bind(
                new PlatformRoleBindingId(701L),
                new SubjectId(101L),
                PlatformRole.PLATFORM_ADMINISTRATOR,
                CREATED_AT);

        assertTrue(binding.isActive());
        assertTrue(binding.permissions().contains(new PermissionCode("platform-session:access")));
        assertTrue(binding.permissions().contains(new PermissionCode("platform:accounts:manage")));
        assertTrue(binding.permissions().contains(new PermissionCode("platform-operations:read")));

        var revoked = binding.revoke(CREATED_AT.plusSeconds(1));

        assertFalse(revoked.isActive());
        assertEquals(1L, revoked.version());
        assertEquals(CREATED_AT.plusSeconds(1), revoked.revokedAt());
    }

    @Test
    void 平台管理员绑定应派生目录治理所需的固定权限() {
        var binding = PlatformRoleBinding.bind(
                new PlatformRoleBindingId(702L),
                new SubjectId(102L),
                PlatformRole.PLATFORM_ADMINISTRATOR,
                CREATED_AT);

        assertTrue(binding.permissions().contains(new PermissionCode("platform-session:access")));
        assertTrue(binding.permissions().contains(new PermissionCode("platform:accounts:manage")));
        assertTrue(binding.permissions().contains(new PermissionCode("tenant-governance:write")));
        assertTrue(binding.permissions().contains(new PermissionCode("model-platform:write")));
        assertTrue(binding.permissions().contains(new PermissionCode("platform-operations:read")));
        assertEquals(5, binding.permissions().size());
    }

    @Test
    void 产品只发布一个平台管理员角色() {
        assertEquals(List.of("PLATFORM_ADMINISTRATOR"),
                java.util.Arrays.stream(PlatformRole.values()).map(Enum::name).toList());
        assertThrows(IllegalArgumentException.class,
                () -> PlatformRole.from("PLATFORM_OPERATOR"));
    }

    @Test
    void 不得将个人权限直接写入平台角色绑定() {
        assertThrows(IllegalArgumentException.class, () -> PlatformRole.from("platform:accounts:manage"));
    }
}
