package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.candidate.PlatformAccountCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.candidate.AccountCandidatePurpose;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.candidate.PlatformAccountCandidateApplicationService;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAccountCandidateApplicationServiceTest {

    @Test
    void 初始租户管理员候选必须由服务端搜索并仅返回安全摘要() {
        var service = new PlatformAccountCandidateApplicationService(
                (query, page) -> PageResult.of(
                        List.of(new PlatformAccountCandidate("9", "tenant.admin", "ACTIVE")), page, 1L));

        var result = service.search(
                platform(), AccountCandidatePurpose.TENANT_INITIAL_ADMINISTRATOR, "tenant", new PageQuery(1L, 20L));

        assertEquals("tenant.admin", result.items().getFirst().loginName());
        assertEquals("9", result.items().getFirst().accountReference());
    }

    @Test
    void 缺少账户管理权限时不得读取候选() {
        var service = new PlatformAccountCandidateApplicationService((query, page) -> PageResult.of(List.of(), page, 0L));

        assertThrows(IllegalArgumentException.class, () -> service.search(
                new PlatformRequestContext(
                        new SubjectId(1L), CredentialType.PERSON, AccessChannel.CONSOLE, 0L,
                        Set.of(PlatformPermissions.SESSION_ACCESS), "trace-no-account"),
                AccountCandidatePurpose.TENANT_INITIAL_ADMINISTRATOR,
                "",
                new PageQuery(1L, 20L)));
    }

    private static PlatformRequestContext platform() {
        return new PlatformRequestContext(
                new SubjectId(1L), CredentialType.PERSON, AccessChannel.CONSOLE, 0L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE), "trace-account");
    }
}
