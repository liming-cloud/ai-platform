package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderModelCandidatesQuery;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelCandidateReadService;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台范围模型候选的安全分页入口。 */
@RestController
@RequestMapping("/api/v1/platform/model-supply/connections")
@ConditionalOnBean(PlatformProviderModelCandidateReadService.class)
public class PlatformProviderModelCandidateController {
    private final PlatformProviderModelCandidateReadService candidates;

    public PlatformProviderModelCandidateController(PlatformProviderModelCandidateReadService candidates) {
        this.candidates = Objects.requireNonNull(candidates, "平台模型候选读取用例不能为空");
    }

    @GetMapping("/{providerConnectionId}/model-candidates")
    public PageResult<PlatformProviderModelCandidateView> list(
            PlatformRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestParam long expectedConnectionVersion,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return candidates.list(new ListPlatformProviderModelCandidatesQuery(
                context, providerConnectionId, expectedConnectionVersion, new PageQuery(pageNumber, pageSize)));
    }
}
