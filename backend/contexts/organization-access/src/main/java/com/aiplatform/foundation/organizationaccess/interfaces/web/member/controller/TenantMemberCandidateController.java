package com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberCandidateApplicationService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前租户管理员为新成员选择账户的安全名称搜索入口。 */
@RestController
@ConditionalOnBean(TenantMemberCandidateApplicationService.class)
@RequestMapping("/api/v1/tenant/member-candidates")
public final class TenantMemberCandidateController {

    private final TenantMemberCandidateApplicationService service;

    public TenantMemberCandidateController(TenantMemberCandidateApplicationService service) {
        this.service = Objects.requireNonNull(service, "租户成员候选服务不能为空");
    }

    @GetMapping
    public PageResult<TenantMemberCandidate> search(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return service.search(context, q, new PageQuery(page, size));
    }
}
