package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberCandidate;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantMemberCandidateApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(4921825928971026433L);

    @Test
    void 应按当前租户排除已有成员并返回名称摘要() {
        var service = new TenantMemberCandidateApplicationService((tenantId, query, page) -> {
            assertEquals(TENANT, tenantId);
            assertEquals("user", query);
            return PageResult.of(
                    List.of(new TenantMemberCandidate("opaque-account", "tenant.user", "ACTIVE")),
                    page,
                    1L);
        });

        var result = service.search(manager(), " user ", new PageQuery(1L, 20L));

        assertEquals("tenant.user", result.items().getFirst().loginName());
    }

    @Test
    void 普通用户不得读取成员候选() {
        var service = new TenantMemberCandidateApplicationService(
                (tenantId, query, page) -> PageResult.of(List.of(), page, 0L));

        assertThrows(IllegalArgumentException.class,
                () -> service.search(user(), "", new PageQuery(1L, 20L)));
    }

    private static TrustedRequestContext manager() {
        return context(Set.of(new PermissionCode("access:manage")));
    }

    private static TrustedRequestContext user() {
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
                "trc_member_candidate");
    }
}
