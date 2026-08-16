package com.aiplatform.foundation.organizationaccess.domain.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantMembershipLifecycleTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T08:00:00Z");

    @Test
    void 停用非最后访问管理员必须推进成员权限版本() {
        var disabled = TenantMembershipLifecycle.disable(enabledMember(), true, 1L);

        assertEquals(TenantMembershipStatus.DISABLED, disabled.status());
        assertEquals(4L, disabled.permissionVersion());
        assertEquals(8L, disabled.version());
    }

    @Test
    void 停用最后一个访问管理员必须失败关闭() {
        assertThrows(IllegalStateException.class,
                () -> TenantMembershipLifecycle.disable(enabledMember(), true, 0L));
    }

    @Test
    void 重复停用已停用成员必须幂等且不阻断当前访问管理员目录() {
        var disabled = TenantMembershipLifecycle.disable(
                enabledMember().disable(), true, 0L);

        assertEquals(TenantMembershipStatus.DISABLED, disabled.status());
        assertEquals(4L, disabled.permissionVersion());
        assertEquals(8L, disabled.version());
    }

    @Test
    void 建立普通成员必须从无权限启用状态开始() {
        var member = TenantMembershipLifecycle.establish(
                new MemberId(3382738893456539651L), TENANT,
                new SubjectId(4921825928971026435L), CREATED_AT);

        assertEquals(TenantMembershipRole.TENANT_USER, member.role());
        assertEquals(TenantMembershipStatus.ENABLED, member.status());
        assertEquals(0L, member.permissionVersion());
    }

    private static TenantMembership enabledMember() {
        return TenantMembership.reconstitute(
                new MemberId(3382738893456539650L),
                TENANT,
                new SubjectId(4921825928971026434L),
                TenantMembershipRole.TENANT_USER,
                TenantMembershipStatus.ENABLED,
                3L,
                7L,
                CREATED_AT);
    }
}
