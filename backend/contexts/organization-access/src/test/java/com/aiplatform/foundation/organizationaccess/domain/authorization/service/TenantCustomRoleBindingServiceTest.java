package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantCustomRoleBindingServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T05:00:00Z");

    @Test
    void 活动自定义角色只能绑定到同租户的已启用成员并推进权限修订() {
        var result = TenantCustomRoleBindingService.bind(member(TENANT, TenantMembershipStatus.ENABLED),
                activeRole(TENANT), NOW);

        assertEquals(1L, result.revisedMembership().permissionVersion());
        assertEquals(new TenantCustomRoleId(9001L), result.binding().customRoleId());
    }

    @Test
    void 停用角色或跨租户角色不得绑定() {
        assertThrows(IllegalStateException.class, () -> TenantCustomRoleBindingService.bind(
                member(TENANT, TenantMembershipStatus.ENABLED), activeRole(TENANT).disable(NOW), NOW));
        assertThrows(IllegalArgumentException.class, () -> TenantCustomRoleBindingService.bind(
                member(TENANT, TenantMembershipStatus.ENABLED),
                activeRole(new TenantId(100000000000000002L)), NOW));
    }

    private static TenantMembership member(TenantId tenantId, TenantMembershipStatus status) {
        return TenantMembership.reconstitute(new MemberId(3382738893456539650L), tenantId,
                new SubjectId(4921825928971026434L), TenantMembershipRole.MEMBER, status, 0L, 1L, NOW);
    }

    private static TenantCustomRole activeRole(TenantId tenantId) {
        return TenantCustomRole.create(new TenantCustomRoleId(9001L), tenantId, "模型观察员",
                Set.of(new PermissionCode("model:read")), NOW);
    }
}
