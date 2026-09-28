package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import org.junit.jupiter.api.Test;

class TenantLoginSecurityPolicyTest {
    private static final TenantId TENANT = new TenantId(100000000000000001L);

    @Test
    void 默认值和观察窗口由已接受合同固定() {
        var policy = TenantLoginSecurityPolicy.defaults(TENANT);
        assertEquals(8, policy.sessionHours());
        assertEquals(5, policy.failedAttempts());
        assertEquals(15, policy.lockMinutes());
        assertEquals(15, policy.observationMinutes());
        assertEquals(0, policy.version());
    }

    @Test
    void 三项上下限与版本均拒绝越界() {
        for (int hours : new int[]{0, 25}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantLoginSecurityPolicy(TENANT, hours, 5, 15, 0));
        }
        for (int failures : new int[]{2, 11}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantLoginSecurityPolicy(TENANT, 8, failures, 15, 0));
        }
        for (int minutes : new int[]{4, 61}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantLoginSecurityPolicy(TENANT, 8, 5, minutes, 0));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TenantLoginSecurityPolicy(TENANT, 8, 5, 15, -1));
    }
}
