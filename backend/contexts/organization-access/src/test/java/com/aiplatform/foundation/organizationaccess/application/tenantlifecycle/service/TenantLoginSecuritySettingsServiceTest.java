package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantLoginSecuritySettingsServiceTest {
    private static final TenantId TENANT = new TenantId(100000000000000001L);

    @Test
    void 无权读取先于存储查询拒绝() {
        var reads = new AtomicInteger();
        var service = new TenantLoginSecuritySettingsService(new TenantLoginSecurityPolicyStore() {
            @Override public Optional<TenantLoginSecurityPolicy> find(long tenantId) {
                reads.incrementAndGet();
                return Optional.of(TenantLoginSecurityPolicy.defaults(TENANT));
            }
            @Override public boolean replace(TenantLoginSecurityPolicy next, long expectedVersion) { return true; }
        }, tenantId -> {});

        assertThrows(RuntimeException.class, () -> service.get(platform(false), TENANT.value()));
        assertEquals(0, reads.get());
    }

    @Test
    void 独立保存只变更目标策略且无变化不撤销() {
        var value = new AtomicReference<>(TenantLoginSecurityPolicy.defaults(TENANT));
        var revocations = new AtomicInteger();
        var service = new TenantLoginSecuritySettingsService(new TenantLoginSecurityPolicyStore() {
            @Override public Optional<TenantLoginSecurityPolicy> find(long tenantId) {
                return tenantId == TENANT.value() ? Optional.of(value.get()) : Optional.empty();
            }
            @Override public boolean replace(TenantLoginSecurityPolicy next, long expectedVersion) {
                if (value.get().version() != expectedVersion) return false;
                value.set(next);
                return true;
            }
        }, tenantId -> { assertEquals(TENANT, tenantId); revocations.incrementAndGet(); });

        var unchanged = service.update(platform(true), TENANT.value(), 8, 5, 15, 0);
        assertEquals(0, unchanged.version());
        assertEquals(0, revocations.get());
        var changed = service.update(platform(true), TENANT.value(), 4, 3, 30, 0);
        assertEquals(1, changed.version());
        assertEquals(4, changed.sessionHours());
        assertEquals(1, revocations.get());
        assertThrows(RuntimeException.class,
                () -> service.update(platform(true), TENANT.value(), 8, 5, 15, 0));
        assertEquals(1, revocations.get());
    }

    private static PlatformRequestContext platform(boolean authorized) {
        return new PlatformRequestContext(new SubjectId(4921825928971026436L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                authorized ? Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE)
                        : Set.of(PlatformPermissions.SESSION_ACCESS),
                "trc_security_settings");
    }
}
