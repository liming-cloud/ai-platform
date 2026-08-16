package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.candidate.AccountCandidatePurpose;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.candidate.PlatformAccountCandidateApplicationService;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 需要关联账户的产品任务所使用的名称搜索入口。 */
@RestController
@ConditionalOnBean(PlatformAccountCandidateApplicationService.class)
@RequestMapping("/api/v1/platform/accounts/candidates")
public final class PlatformAccountCandidateController {

    private final PlatformAccountCandidateApplicationService service;

    public PlatformAccountCandidateController(PlatformAccountCandidateApplicationService service) {
        this.service = Objects.requireNonNull(service, "平台账户候选服务不能为空");
    }

    @GetMapping
    public PageResult<PlatformAccountCandidate> search(
            PlatformRequestContext context,
            @RequestParam AccountCandidatePurpose purpose,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return service.search(context, purpose, q, new PageQuery(page, size));
    }
}
