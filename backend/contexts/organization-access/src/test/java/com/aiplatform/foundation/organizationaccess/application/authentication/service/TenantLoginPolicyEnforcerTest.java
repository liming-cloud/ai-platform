package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TenantLoginPolicyEnforcerTest {
    private static final TenantId TENANT = new TenantId(201L);
    private static final SubjectId SUBJECT = new SubjectId(301L);
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void 失败计数与正确口令锁定检查共用目标租户策略() {
        var failures = new AtomicInteger();
        var clears = new AtomicInteger();
        var locked = new AtomicBoolean();
        TenantLoginAttemptStore attempts = new TenantLoginAttemptStore() {
            @Override public boolean isLocked(TenantId tenantId, SubjectId subjectId, Instant now) {
                assertEquals(TENANT, tenantId);
                assertEquals(SUBJECT, subjectId);
                return locked.get();
            }
            @Override public void recordFailure(TenantId tenantId, SubjectId subjectId,
                    TenantLoginSecurityPolicy policy, Instant now) { failures.incrementAndGet(); }
            @Override public boolean clearIfUnlocked(TenantId tenantId, SubjectId subjectId, Instant now) {
                clears.incrementAndGet();
                return !locked.get();
            }
        };
        TenantLoginSecurityPolicyStore policies = new TenantLoginSecurityPolicyStore() {
            @Override public Optional<TenantLoginSecurityPolicy> find(long tenantId) {
                return Optional.of(new TenantLoginSecurityPolicy(TENANT, 4, 3, 30, 1));
            }
            @Override public boolean replace(TenantLoginSecurityPolicy next, long expectedVersion) {
                throw new AssertionError("认证不写配置");
            }
        };
        var service = new TenantLoginPolicyEnforcer(policies, attempts, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(InvalidCredentialsException.class,
                () -> service.afterPasswordCheck(TENANT, SUBJECT, false));
        assertEquals(1, failures.get());
        assertEquals(0, clears.get());
        assertEquals(4, service.afterPasswordCheck(TENANT, SUBJECT, true).toHours());
        assertEquals(1, clears.get());
        locked.set(true);
        assertThrows(InvalidCredentialsException.class,
                () -> service.afterPasswordCheck(TENANT, SUBJECT, true));
        assertEquals(1, clears.get());
    }
}
