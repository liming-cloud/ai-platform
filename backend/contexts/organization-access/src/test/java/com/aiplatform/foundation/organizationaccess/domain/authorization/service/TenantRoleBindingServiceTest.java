package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 验证角色绑定只能在成员所属租户内改变权限修订。
 */
class TenantRoleBindingServiceTest {

    private static final TenantId STORE = new TenantId(100000000000000001L);
    private static final TenantId CAMPUS = new TenantId(100000000000000002L);
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T01:00:00Z");

    @Test
    void 绑定内置访问管理员角色应递增目标成员的权限修订() {
        var member = member(STORE, 0L);

        var result = TenantRoleBindingService.bind(
                member, STORE, TenantBuiltInRole.TENANT_ACCESS_ADMINISTRATOR, OCCURRED_AT);

        assertEquals(1L, result.revisedMembership().permissionVersion());
        assertEquals(STORE, result.binding().tenantId());
        assertEquals(member.id(), result.binding().membershipId());
        assertEquals(TenantBuiltInRole.TENANT_ACCESS_ADMINISTRATOR, result.binding().role());
        assertEquals(OCCURRED_AT, result.occurredAt());
    }

    @Test
    void 不得为其他租户的成员建立角色绑定() {
        var member = member(STORE, 4L);

        assertThrows(
                IllegalArgumentException.class,
                () -> TenantRoleBindingService.bind(
                        member, CAMPUS, TenantBuiltInRole.TENANT_ACCESS_ADMINISTRATOR, OCCURRED_AT));
    }

    @Test
    void 未启用成员不得绑定角色() {
        var member = TenantMembership.reconstitute(
                new MemberId(3382738893456539650L),
                STORE,
                new SubjectId(4921825928971026434L),
                TenantMembershipRole.MEMBER,
                TenantMembershipStatus.DISABLED,
                0L,
                2L,
                OCCURRED_AT.minusSeconds(60));

        assertThrows(
                IllegalStateException.class,
                () -> TenantRoleBindingService.bind(
                        member, STORE, TenantBuiltInRole.TENANT_ACCESS_ADMINISTRATOR, OCCURRED_AT));
    }

    private static TenantMembership member(TenantId tenantId, long permissionVersion) {
        return TenantMembership.reconstitute(
                new MemberId(3382738893456539650L),
                tenantId,
                new SubjectId(4921825928971026434L),
                TenantMembershipRole.MEMBER,
                TenantMembershipStatus.ENABLED,
                permissionVersion,
                2L,
                OCCURRED_AT.minusSeconds(60));
    }
}
