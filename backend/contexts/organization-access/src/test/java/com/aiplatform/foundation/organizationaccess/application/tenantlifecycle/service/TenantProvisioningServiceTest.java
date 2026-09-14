package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import com.aiplatform.foundation.security.identity.*;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantProvisioningServiceTest {
    @Test
    void 成功必须新建唯一身份并激活且清除密码数组() {
        var committed = new AtomicReference<TenantProvisioningResult>();
        var calls = new AtomicInteger();
        var service = service(true, committed, calls, false);
        var password = "临时测试密码".toCharArray();
        var result = service.provision(platform(true), command(), password);
        assertEquals("ACTIVE", result.status());
        assertEquals(1L, result.version());
        assertEquals(result.initialAdministratorSubjectId(), committed.get().administratorIdentity().id().value());
        assertEquals("TENANT_ADMINISTRATOR", committed.get().initialAdministratorRoleBinding().role().name());
        assertArrayEquals(new char[password.length], password);
        assertEquals(1, calls.get());
        assertFalse(result.toString().contains("测试摘要"));
    }

    @Test
    void 无权限和环境未就绪均须先于密码计算拒绝且清理数组() {
        for (boolean ready : new boolean[] {false, true}) {
            var committed = new AtomicReference<TenantProvisioningResult>();
            var calls = new AtomicInteger();
            var password = "临时测试密码".toCharArray();
            var service = service(ready, committed, calls, false);
            var failure = assertThrows(ApplicationException.class,
                    () -> service.provision(platform(!ready), command(), password));
            assertNotNull(failure);
            assertNull(committed.get());
            assertEquals(0, calls.get());
            assertArrayEquals(new char[password.length], password);
        }
    }

    @Test
    void 持久化异常也必须清除密码() {
        var password = "临时测试密码".toCharArray();
        var service = service(true, new AtomicReference<>(), new AtomicInteger(), true);
        assertThrows(IllegalStateException.class, () -> service.provision(platform(true), command(), password));
        assertArrayEquals(new char[password.length], password);
    }

    private static TenantProvisioningService service(boolean ready, AtomicReference<TenantProvisioningResult> committed,
            AtomicInteger hashes, boolean fail) {
        var store = new TenantProvisioningStore() {
            @Override public boolean authenticationReady() { return ready; }
            @Override public void commit(TenantProvisioningResult result) {
                if (fail) { throw new IllegalStateException("受控写入故障"); }
                committed.set(result);
            }
        };
        var ids = new AtomicLong(100000000000000000L);
        return new TenantProvisioningService(store, ids::incrementAndGet,
                password -> { hashes.incrementAndGet(); return "测试摘要仅限单元测试"; }, Clock.systemUTC());
    }

    private static TenantProvisioningCommand command() {
        return new TenantProvisioningCommand("fresh-tenant", "新租户", "new.admin", 3, 1000,
                10000000000L, 50000000L, 1048576L);
    }

    private static PlatformRequestContext platform(boolean authorized) {
        return new PlatformRequestContext(new SubjectId(4921825928971026436L), CredentialType.PERSON,
                AccessChannel.CONSOLE, 1L, authorized ? Set.of(PlatformPermissions.SESSION_ACCESS,
                PlatformPermissions.TENANT_GOVERNANCE_WRITE) : Set.of(PlatformPermissions.SESSION_ACCESS), "trc_onboarding");
    }
}
