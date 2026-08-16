package com.aiplatform.foundation.knowledgedata.interfaces.web.datasource.controller;

import com.aiplatform.foundation.knowledgedata.application.datasource.dto.PlatformObjectRangeCandidate;
import com.aiplatform.foundation.knowledgedata.application.datasource.service.PlatformObjectRangeCandidateService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前知识空间可用对象范围的名称化服务端分页入口。 */
@RestController
@ConditionalOnBean(PlatformObjectRangeCandidateService.class)
@RequestMapping("/api/v1/knowledge-spaces/{knowledgeSpaceId}/object-storage-ranges")
public final class PlatformObjectRangeCandidateController {
    private final PlatformObjectRangeCandidateService service;

    public PlatformObjectRangeCandidateController(PlatformObjectRangeCandidateService service) {
        this.service = Objects.requireNonNull(service, "对象范围候选服务不能为空");
    }

    @GetMapping
    public PageResult<PlatformObjectRangeCandidate> page(
            TrustedRequestContext context,
            @PathVariable String knowledgeSpaceId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return service.page(context, knowledgeSpaceId, new PageQuery(page, size));
    }
}
