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
import org.junit.jupiter.api.Test;

class PlatformRoleBindingTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void 内置平台角色绑定应从角色模板派生权限并可撤销() {
        var binding = PlatformRoleBinding.bind(
                new PlatformRoleBindingId(701L),
                new SubjectId(101L),
                PlatformRole.PLATFORM_ACCOUNT_ADMINISTRATOR,
                CREATED_AT);

        assertTrue(binding.isActive());
        assertTrue(binding.permissions().contains(new PermissionCode("platform-session:access")));
        assertTrue(binding.permissions().contains(new PermissionCode("platform:accounts:manage")));

        var revoked = binding.revoke(CREATED_AT.plusSeconds(1));

        assertFalse(revoked.isActive());
        assertEquals(1L, revoked.version());
        assertEquals(CREATED_AT.plusSeconds(1), revoked.revokedAt());
    }

    @Test
    void 不得将个人权限直接写入平台角色绑定() {
        assertThrows(IllegalArgumentException.class, () -> PlatformRole.from("platform:accounts:manage"));
    }
}
