package com.aiplatform.foundation.knowledgedata.application.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.PlatformObjectRangeCandidate;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformObjectRangeCandidateServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);

    @Test
    void 应按当前租户和知识空间返回名称化范围() {
        var service = new PlatformObjectRangeCandidateService((tenantId, spaceId, page) -> {
            assertEquals(TENANT, tenantId);
            assertEquals("200000000000000001", spaceId.toString());
            return PageResult.of(List.of(new PlatformObjectRangeCandidate(
                    "opaque-range", 3L, "已授权对象范围 · 4 个对象", 4)), page, 1L);
        });

        var result = service.page(context("knowledge:write"), "200000000000000001", new PageQuery(1, 20));

        assertEquals("已授权对象范围 · 4 个对象", result.items().getFirst().displayName());
    }

    @Test
    void 普通用户不得读取对象范围候选() {
        var service = new PlatformObjectRangeCandidateService(
                (tenantId, spaceId, page) -> PageResult.of(List.of(), page, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.page(context("knowledge:read"), "200000000000000001", new PageQuery(1, 20)));
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(TENANT, new SubjectId(100000000000000002L),
                new MembershipId(100000000000000003L), CredentialType.PERSON, AccessChannel.CONSOLE,
                1L, Set.of(new PermissionCode(permission)), "trc_object_range_candidate");
    }
}
