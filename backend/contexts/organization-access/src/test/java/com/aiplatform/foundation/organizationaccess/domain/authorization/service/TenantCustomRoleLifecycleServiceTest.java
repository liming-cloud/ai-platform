package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantCustomRoleLifecycleServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Test
    void 修改活动角色时同时推进所有受影响成员的权限修订() {
        var result = TenantCustomRoleLifecycleService.changePermissions(
                role(Set.of(new PermissionCode("model:read"))),
                Set.of(new PermissionCode("model:write")),
                List.of(member(9001L), member(9002L)),
                1L,
                NOW);

        assertEquals(1L, result.role().version());
        assertEquals(Set.of(new PermissionCode("model:write")), result.role().permissions());
        assertEquals(List.of(1L, 1L), result.revisedMemberships().stream()
                .map(TenantMembership::permissionVersion).toList());
    }

    @Test
    void 移除唯一访问管理权限时拒绝修改角色() {
        assertThrows(IllegalStateException.class, () -> TenantCustomRoleLifecycleService.changePermissions(
                role(Set.of(new PermissionCode("access:manage"))),
                Set.of(new PermissionCode("model:read")),
                List.of(member(9001L)),
                0L,
                NOW));
    }

    @Test
    void 停用活动角色时保留绑定历史但使受影响成员权限失效() {
        var result = TenantCustomRoleLifecycleService.disable(
                role(Set.of(new PermissionCode("model:read"))),
                List.of(member(9001L)),
                1L,
                NOW);

        assertFalse(result.role().isActive());
        assertEquals(1L, result.revisedMemberships().getFirst().permissionVersion());
    }

    private static TenantCustomRole role(Set<PermissionCode> permissions) {
        return TenantCustomRole.create(new TenantCustomRoleId(8001L), TENANT, "租户模型管理员", permissions, NOW);
    }

    private static TenantMembership member(long memberId) {
        return TenantMembership.reconstitute(new MemberId(memberId), TENANT, new SubjectId(memberId + 10000L),
                TenantMembershipRole.TENANT_USER, TenantMembershipStatus.ENABLED, 0L, 0L, NOW);
    }
}
