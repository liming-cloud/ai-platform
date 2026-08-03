package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantLifecycleTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void 应只允许建立激活挂起恢复的可复核状态迁移() {
        var provisioning = TenantLifecycle.provision(
                new TenantId(201L), "north-campus", "北区校园", new SubjectId(101L), CREATED_AT);

        assertEquals(TenantLifecycleStatus.PROVISIONING, provisioning.status());
        assertEquals(0L, provisioning.version());
        assertEquals(new SubjectId(101L), provisioning.initialAdministratorId());
        assertThrows(IllegalStateException.class, provisioning::suspend);

        var active = provisioning.activate();
        var suspended = active.suspend();
        var restored = suspended.restore();

        assertEquals(TenantLifecycleStatus.ACTIVE, active.status());
        assertEquals(1L, active.version());
        assertEquals(TenantLifecycleStatus.SUSPENDED, suspended.status());
        assertEquals(2L, suspended.version());
        assertEquals(TenantLifecycleStatus.ACTIVE, restored.status());
        assertEquals(3L, restored.version());
        assertThrows(IllegalStateException.class, restored::restore);
    }

    @Test
    void 应拒绝不合法的租户编码显示名称和初始管理员() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantLifecycle.provision(
                        new TenantId(201L), "平台", "北区校园", new SubjectId(101L), CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantLifecycle.provision(
                        new TenantId(201L), "north-campus", " ", new SubjectId(101L), CREATED_AT));
        assertThrows(
                NullPointerException.class,
                () -> TenantLifecycle.provision(
                        new TenantId(201L), "north-campus", "北区校园", null, CREATED_AT));
    }
}
