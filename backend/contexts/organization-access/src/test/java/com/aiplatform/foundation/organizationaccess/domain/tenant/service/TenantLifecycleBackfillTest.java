package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantLifecycleBackfillTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final SubjectId SUBJECT_ID = new SubjectId(4921825928971026434L);
    private static final MemberId MEMBERSHIP_ID = new MemberId(3382738893456539650L);
    private static final Instant TENANT_CREATED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant BACKFILLED_AT = Instant.parse("2026-08-03T01:00:00Z");

    @Test
    void 启用历史租户必须经合法激活达到活跃状态() {
        var result = TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.ACTIVE,
                activeSubject(),
                enabledMembership(),
                TENANT_CREATED_AT,
                BACKFILLED_AT);

        assertEquals(TenantLifecycleStatus.ACTIVE, result.lifecycle().status());
        assertEquals(1L, result.lifecycle().version());
        assertEquals(TENANT_CREATED_AT, result.lifecycle().createdAt());
        assertEquals(TenantMembershipRole.TENANT_ADMINISTRATOR, result.initialAdministrator().role());
        assertEquals(List.of(TenantLifecycleStatus.PROVISIONING, TenantLifecycleStatus.ACTIVE),
                result.lifecycleChanges().stream().map(change -> change.status()).toList());
    }

    @Test
    void 禁用历史租户必须经合法激活再挂起达到挂起状态() {
        var result = TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.SUSPENDED,
                activeSubject(),
                enabledMembership(),
                TENANT_CREATED_AT,
                BACKFILLED_AT);

        assertEquals(TenantLifecycleStatus.SUSPENDED, result.lifecycle().status());
        assertEquals(2L, result.lifecycle().version());
        assertEquals(List.of(
                        TenantLifecycleStatus.PROVISIONING,
                        TenantLifecycleStatus.ACTIVE,
                        TenantLifecycleStatus.SUSPENDED),
                result.lifecycleChanges().stream().map(change -> change.status()).toList());
    }

    @Test
    void 缺少经确认的初始管理员映射不得回填生命周期() {
        assertThrows(IllegalArgumentException.class, () -> TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.ACTIVE,
                null,
                enabledMembership(),
                TENANT_CREATED_AT,
                BACKFILLED_AT));
    }

    @Test
    void 不可证明主体与成员归属或启用状态时不得回填() {
        assertThrows(IllegalArgumentException.class, () -> TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.ACTIVE,
                activeSubject(),
                TenantMembership.reconstitute(
                        MEMBERSHIP_ID,
                        new TenantId(TENANT_ID.value() + 1),
                        SUBJECT_ID,
                        TenantMembershipRole.TENANT_USER,
                        TenantMembershipStatus.ENABLED,
                        3L,
                        7L,
                        TENANT_CREATED_AT),
                TENANT_CREATED_AT,
                BACKFILLED_AT));
        assertThrows(IllegalStateException.class, () -> TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.ACTIVE,
                activeSubject().disable(),
                enabledMembership(),
                TENANT_CREATED_AT,
                BACKFILLED_AT));
        assertThrows(IllegalStateException.class, () -> TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.ACTIVE,
                activeSubject(),
                TenantMembership.reconstitute(
                        MEMBERSHIP_ID,
                        TENANT_ID,
                        SUBJECT_ID,
                        TenantMembershipRole.TENANT_USER,
                        TenantMembershipStatus.DISABLED,
                        3L,
                        7L,
                        TENANT_CREATED_AT),
                TENANT_CREATED_AT,
                BACKFILLED_AT));
        assertThrows(IllegalArgumentException.class, () -> TenantLifecycleBackfill.backfill(
                TENANT_ID,
                "demo-store",
                "演示门店",
                TenantLifecycleStatus.PROVISIONING,
                activeSubject(),
                enabledMembership(),
                TENANT_CREATED_AT,
                BACKFILLED_AT));
    }

    private static SubjectIdentity activeSubject() {
        return SubjectIdentity.register(
                SUBJECT_ID, "admin", new EncodedPassword("valid"), TENANT_CREATED_AT);
    }

    private static TenantMembership enabledMembership() {
        return TenantMembership.reconstitute(
                MEMBERSHIP_ID,
                TENANT_ID,
                SUBJECT_ID,
                TenantMembershipRole.TENANT_USER,
                TenantMembershipStatus.ENABLED,
                3L,
                7L,
                TENANT_CREATED_AT);
    }
}
