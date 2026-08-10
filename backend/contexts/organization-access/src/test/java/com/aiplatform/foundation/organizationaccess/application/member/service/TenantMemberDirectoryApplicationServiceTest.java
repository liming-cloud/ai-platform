package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberSummary;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantMemberDirectoryApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);

    @Test
    void 访问管理员应分页读取当前租户的最小成员目录() {
        var service = new TenantMemberDirectoryApplicationService(new TenantMemberDirectory() {
            @Override
            public PageResult<TenantMemberSummary> page(TenantId tenantId, PageQuery page) {
                assertEquals(TENANT, tenantId);
                assertEquals(new PageQuery(2L, 20L), page);
                return PageResult.of(List.of(new TenantMemberSummary(
                        "3382738893456539650", "admin", "TENANT_ADMINISTRATOR",
                        "ENABLED", 2L, Instant.parse("2026-08-10T08:00:00Z"))), page, 21L);
            }
        });

        var result = service.list(accessManager(), new PageQuery(2L, 20L));

        assertEquals(21L, result.totalElements());
        assertEquals("admin", result.items().getFirst().loginName());
    }

    @Test
    void 无成员治理权限的租户会话不得读取成员目录() {
        var service = new TenantMemberDirectoryApplicationService((tenantId, page) -> {
            throw new AssertionError("未授权请求不得触发成员目录读取");
        });

        assertThrows(IllegalArgumentException.class,
                () -> service.list(member(), new PageQuery(1L, 20L)));
    }

    private static TrustedRequestContext accessManager() {
        return context(Set.of(new PermissionCode("access:manage")));
    }

    private static TrustedRequestContext member() {
        return context(Set.of(new PermissionCode("knowledge:read")));
    }

    private static TrustedRequestContext context(Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                permissions,
                "trc_member_directory");
    }
}
