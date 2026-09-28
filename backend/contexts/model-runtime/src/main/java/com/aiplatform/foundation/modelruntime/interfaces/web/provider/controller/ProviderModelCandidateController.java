package com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller;

import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryCandidateReadService;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
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

/** 当前连接修订候选模型的按需读取入口。 */
@RequiresTenantFeature("modelGovernance")
@RestController
@RequestMapping("/api/v1/model-providers")
@ConditionalOnBean(ProviderModelDiscoveryCandidateReadService.class)
public class ProviderModelCandidateController {

    private final ProviderModelDiscoveryCandidateReadService candidates;

    public ProviderModelCandidateController(ProviderModelDiscoveryCandidateReadService candidates) {
        this.candidates = Objects.requireNonNull(candidates, "当前模型候选读取用例不能为空");
    }

    /**
     * 仅返回当前连接修订、成功且未过期发现的安全候选；租户范围始终从令牌恢复。
     */
    @GetMapping("/{providerConnectionId}/current-model-candidates")
    public PageResult<ProviderModelCandidateView> listCurrentCandidates(
            TrustedRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return candidates.listCurrentCandidates(
                context,
                providerConnectionId,
                new PageQuery(pageNumber, pageSize));
    }
}
