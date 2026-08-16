package com.aiplatform.foundation.organizationaccess.domain.member.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TenantMembershipTest {

    @Test
    void 产品成员职责只保留租户管理员和普通用户() {
        assertEquals(
                List.of("TENANT_ADMINISTRATOR", "TENANT_USER"),
                java.util.Arrays.stream(TenantMembershipRole.values()).map(Enum::name).toList());
    }

    @Test
    void 启停成员资格均推进权限修订且不改变主体租户归属() {
        var membership = TenantMembership.establishInitialAdministrator(
                new MemberId(730000000000000021L),
                new TenantId(730000000000000001L),
                new SubjectId(730000000000000031L),
                Instant.parse("2026-08-10T03:00:00Z"));

        var disabled = membership.disable();
        var enabled = disabled.enable();

        assertEquals(TenantMembershipStatus.DISABLED, disabled.status());
        assertEquals(1L, disabled.permissionVersion());
        assertEquals(1L, disabled.version());
        assertEquals(membership.tenantId(), disabled.tenantId());
        assertEquals(membership.subjectId(), disabled.subjectId());
        assertEquals(TenantMembershipStatus.ENABLED, enabled.status());
        assertEquals(2L, enabled.permissionVersion());
        assertEquals(2L, enabled.version());
    }
}
