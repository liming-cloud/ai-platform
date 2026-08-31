package com.aiplatform.foundation.organizationaccess.domain.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleErrorCode;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantAdministratorGovernanceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-31T02:00:00Z");

    @Test
    void 平台可将活动主体建立为租户管理员() {
        var assignment = TenantAdministratorGovernance.assign(
                TENANT,
                subject(11L, "new-admin"),
                Optional.empty(),
                new MemberId(101L),
                NOW);

        assertTrue(assignment.newlyEstablished());
        assertEquals(TenantMembershipRole.TENANT_ADMINISTRATOR, assignment.administrator().role());
        assertEquals(TenantMembershipStatus.ENABLED, assignment.administrator().status());
        assertEquals("TENANT_ADMINISTRATOR", assignment.roleBinding().role().name());
    }

    @Test
    void 平台可把当前租户普通成员提升为管理员且只推进一次权限修订() {
        var ordinary = membership(102L, 12L, TenantMembershipRole.TENANT_USER, TenantMembershipStatus.ENABLED, 4L, 7L);

        var assignment = TenantAdministratorGovernance.assign(
                TENANT,
                subject(12L, "existing-user"),
                Optional.of(ordinary),
                new MemberId(999L),
                NOW);

        assertFalse(assignment.newlyEstablished());
        assertEquals(new MemberId(102L), assignment.administrator().id());
        assertEquals(TenantMembershipRole.TENANT_ADMINISTRATOR, assignment.administrator().role());
        assertEquals(5L, assignment.administrator().permissionVersion());
        assertEquals(8L, assignment.administrator().version());
    }

    @Test
    void 不能停用租户最后一个启用管理员() {
        var administrator = membership(
                103L, 13L, TenantMembershipRole.TENANT_ADMINISTRATOR, TenantMembershipStatus.ENABLED, 1L, 2L);

        var rejected = assertThrows(OrganizationAccessRuleViolation.class,
                () -> TenantAdministratorGovernance.disable(administrator, 0L));

        assertEquals(OrganizationAccessRuleErrorCode.LAST_ENABLED_TENANT_ADMINISTRATOR,
                rejected.errorCode());
    }

    @Test
    void 替换必须同时启用新管理员并停用旧管理员() {
        var previous = membership(
                104L, 14L, TenantMembershipRole.TENANT_ADMINISTRATOR, TenantMembershipStatus.ENABLED, 2L, 3L);
        var assignment = TenantAdministratorGovernance.assign(
                TENANT,
                subject(15L, "replacement-admin"),
                Optional.empty(),
                new MemberId(105L),
                NOW);

        var replacement = TenantAdministratorGovernance.replace(previous, assignment);

        assertEquals(TenantMembershipStatus.ENABLED, replacement.assignment().administrator().status());
        assertEquals(TenantMembershipStatus.DISABLED, replacement.previousAdministrator().status());
    }

    @Test
    void 恢复管理员必须保持管理员角色并推进一次权限修订() {
        var disabled = membership(
                106L, 16L, TenantMembershipRole.TENANT_ADMINISTRATOR, TenantMembershipStatus.DISABLED, 3L, 4L);

        var restored = TenantAdministratorGovernance.restore(subject(16L, "restored-admin"), disabled);

        assertEquals(TenantMembershipRole.TENANT_ADMINISTRATOR, restored.role());
        assertEquals(TenantMembershipStatus.ENABLED, restored.status());
        assertEquals(4L, restored.permissionVersion());
        assertEquals(5L, restored.version());
    }

    private static SubjectIdentity subject(long id, String loginName) {
        return SubjectIdentity.register(
                new SubjectId(id), loginName, new EncodedPassword("encoded-password"), NOW);
    }

    private static TenantMembership membership(
            long membershipId,
            long subjectId,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            long permissionVersion,
            long version) {
        return TenantMembership.reconstitute(
                new MemberId(membershipId),
                TENANT,
                new SubjectId(subjectId),
                role,
                status,
                permissionVersion,
                version,
                NOW);
    }
}
