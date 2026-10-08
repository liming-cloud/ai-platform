package com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller;

import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import org.springframework.web.bind.annotation.*;

/** 管理员读取当前租户实际已发布助手；前端不能提交租户范围或内部权限。 */
@RestController
@RequestMapping("/api/v1/tenant/member-assistants")
public final class MemberAssistantCandidateController {
    private final MemberAccountApplicationService accounts;
    public MemberAssistantCandidateController(MemberAccountApplicationService accounts) {
        this.accounts = java.util.Objects.requireNonNull(accounts);
    }
    @GetMapping
    public PageResult<com.aiplatform.foundation.organizationaccess.application.member.dto.MemberAssistantChoice> list(TrustedRequestContext context,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return accounts.candidates(context, new PageQuery(pageNumber, pageSize), q);
    }
}
