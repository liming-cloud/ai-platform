package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class TenantLifecycleQueryServiceTest {

    @Test
    void 缺少治理写权限时不得触达生命周期读取端口() {
        var read = new AtomicBoolean();
        var port = org.mockito.Mockito.mock(TenantLifecycleReadPort.class);
        org.mockito.Mockito.when(port.page(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            read.set(true);
            return PageResult.of(List.of(), invocation.getArgument(0), 0L);
        });
        var service = new TenantLifecycleQueryService(port);

        assertThrows(IllegalArgumentException.class, () -> service.list(
                platformContext(Set.of(PlatformPermissions.SESSION_ACCESS)), new PageQuery(1L, 20L), "", ""));

        assertFalse(read.get());
    }

    @Test
    void 受权平台上下文只得到端口返回的最小投影() {
        var summary = new TenantLifecycleSummary(
                100000000000000001L,
                "demo-store",
                "门店演示租户",
                "ACTIVE",
                3L,
                Instant.parse("2026-08-03T03:00:00Z"),
                Instant.parse("2026-08-03T04:00:00Z"), 1L);
        var port = org.mockito.Mockito.mock(TenantLifecycleReadPort.class);
        org.mockito.Mockito.when(port.page(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                PageResult.of(List.of(summary), invocation.getArgument(0), 1L));
        var service = new TenantLifecycleQueryService(port);

        var page = service.list(
                platformContext(Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE)),
                new PageQuery(1L, 20L), "", "");

        assertEquals(List.of(summary), page.items());
        assertEquals(1L, page.totalElements());
        assertTrue(page.items().getFirst().lastChangedAt().isAfter(page.items().getFirst().createdAt()));
    }

    @Test
    void 无权查询先拒绝而不读取或解析筛选事实() {
        var port = org.mockito.Mockito.mock(TenantLifecycleReadPort.class);
        var service = new TenantLifecycleQueryService(port);
        var error = assertThrows(IllegalArgumentException.class, () -> service.list(
                platformContext(Set.of(PlatformPermissions.SESSION_ACCESS)), new PageQuery(1, 20), "x".repeat(65), "INVALID"));
        assertEquals("平台上下文缺少租户治理权限", error.getMessage());
        org.mockito.Mockito.verifyNoInteractions(port);
    }

    @Test
    void 受权过滤在读取前规范化且拒绝未知状态() {
        var port = org.mockito.Mockito.mock(TenantLifecycleReadPort.class);
        var service = new TenantLifecycleQueryService(port);
        var context = platformContext(Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE));
        service.list(context, new PageQuery(2, 10), " demo ", "ACTIVE");
        org.mockito.Mockito.verify(port).page(new PageQuery(2, 10),
                new com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantDirectoryFilter("demo", "ACTIVE"));
        org.mockito.Mockito.clearInvocations(port);
        assertThrows(IllegalArgumentException.class, () -> service.list(context, new PageQuery(1, 20), "", "DISABLED"));
        org.mockito.Mockito.verifyNoInteractions(port);
    }

    private static PlatformRequestContext platformContext(Set<PermissionCode> permissions) {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026436L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                permissions,
                "trc_tenant_lifecycle_query");
    }
}
