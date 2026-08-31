package com.aiplatform.foundation.organizationaccess.domain.member.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantMemberGovernanceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);

    @Test
    void 租户成员治理只允许普通成员目标() {
        assertThrows(
                OrganizationAccessRuleViolation.class,
                () -> TenantMemberGovernance.requireOrdinaryTarget(member(TenantMembershipRole.TENANT_ADMINISTRATOR)));
        assertDoesNotThrow(
                () -> TenantMemberGovernance.requireOrdinaryTarget(member(TenantMembershipRole.TENANT_USER)));
    }

    @Test
    void 租户成员治理不得分配或撤销管理员角色() {
        assertThrows(
                OrganizationAccessRuleViolation.class,
                () -> TenantMemberGovernance.requireOrdinaryRole(TenantBuiltInRole.TENANT_ADMINISTRATOR));
        assertDoesNotThrow(
                () -> TenantMemberGovernance.requireOrdinaryRole(TenantBuiltInRole.TENANT_USER));
    }

    private static TenantMembership member(TenantMembershipRole role) {
        return TenantMembership.reconstitute(
                new MemberId(3382738893456539650L),
                TENANT,
                new SubjectId(4921825928971026434L),
                role,
                TenantMembershipStatus.ENABLED,
                0L,
                0L,
                Instant.parse("2026-08-31T00:00:00Z"));
    }
}
