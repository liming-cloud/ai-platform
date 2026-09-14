package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantProvisioningTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000009L);
    private static final SubjectId SUBJECT_ID = new SubjectId(4921825928971026434L);
    private static final MemberId MEMBERSHIP_ID = new MemberId(3382738893456539660L);
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void 应以启用主体建立不可变初始管理员成员资格() {
        var lifecycle = lifecycle(SUBJECT_ID);

        var result = TenantProvisioning.provision(
                lifecycle, activeSubject(), MEMBERSHIP_ID, quotas(), OCCURRED_AT);

        assertEquals("ACTIVE", result.lifecycle().status().name());
        assertEquals(TENANT_ID, result.initialAdministrator().tenantId());
        assertEquals(SUBJECT_ID, result.initialAdministrator().subjectId());
        assertEquals(MEMBERSHIP_ID, result.initialAdministrator().id());
        assertEquals(
                TenantMembershipRole.TENANT_ADMINISTRATOR,
                result.initialAdministrator().role());
        assertEquals(TENANT_ID, result.lifecycleChange().tenantId());
        assertEquals(1L, result.lifecycleChange().version());
        assertEquals(OCCURRED_AT, result.occurredAt());
    }

    @Test
    void 禁用主体或不匹配初始管理员不得产生半成品() {
        assertThrows(
                IllegalStateException.class,
                () -> TenantProvisioning.provision(
                        lifecycle(SUBJECT_ID),
                        activeSubject().disable(),
                        MEMBERSHIP_ID,
                        quotas(),
                        OCCURRED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantProvisioning.provision(
                        lifecycle(SUBJECT_ID),
                        SubjectIdentity.register(
                                new SubjectId(SUBJECT_ID.value() + 1),
                                "other-admin",
                                new EncodedPassword("valid"),
                                OCCURRED_AT),
                        MEMBERSHIP_ID,
                        quotas(),
                        OCCURRED_AT));
    }

    private static TenantLifecycle lifecycle(SubjectId initialAdministrator) {
        return TenantLifecycle.provision(
                TENANT_ID,
                "demo-tenant",
                "演示租户",
                initialAdministrator,
                OCCURRED_AT);
    }

    private static SubjectIdentity activeSubject() {
        return SubjectIdentity.register(
                SUBJECT_ID,
                "admin",
                new EncodedPassword("valid"),
                OCCURRED_AT);
    }

    private static TenantQuota quotas() {
        return new TenantQuota(TENANT_ID, 3, 1000, 10000000000L, 50000000L, 1048576L, 0L);
    }
}
