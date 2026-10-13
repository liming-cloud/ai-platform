package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderModelCandidatesQuery;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelCandidateReadService;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelCandidateConfirmationService;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ConfirmPlatformProviderModelCandidateCommand;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformProviderModelCandidateConfirmationRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 平台范围模型候选的安全分页入口。 */
@RestController
@RequestMapping("/api/v1/platform/model-supply/connections")
@ConditionalOnBean({
    PlatformProviderModelCandidateReadService.class,
    PlatformProviderModelCandidateConfirmationService.class
})
public class PlatformProviderModelCandidateController {
    private final PlatformProviderModelCandidateReadService candidates;
    private final PlatformProviderModelCandidateConfirmationService confirmations;

    public PlatformProviderModelCandidateController(
            PlatformProviderModelCandidateReadService candidates,
            PlatformProviderModelCandidateConfirmationService confirmations) {
        this.candidates = Objects.requireNonNull(candidates, "平台模型候选读取用例不能为空");
        this.confirmations = Objects.requireNonNull(confirmations, "平台模型候选确认用例不能为空");
    }

    @GetMapping("/{providerConnectionId}/model-candidates")
    public PageResult<PlatformProviderModelCandidateView> list(
            PlatformRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestParam long expectedConnectionVersion,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(defaultValue = "") String q,
            @RequestParam org.springframework.util.MultiValueMap<String, String> parameters) {
        if (parameters.entrySet().stream().anyMatch(entry -> !java.util.Set.of("expectedConnectionVersion", "pageNumber", "pageSize", "q").contains(entry.getKey()) || entry.getValue().size() != 1)) {
            throw new IllegalArgumentException("平台候选目录不接受范围、重复或未知参数");
        }
        return candidates.list(new ListPlatformProviderModelCandidatesQuery(
                context, providerConnectionId, expectedConnectionVersion, new PageQuery(pageNumber, pageSize), q));
    }

    @PostMapping("/{providerConnectionId}/model-candidates/confirmation")
    public PlatformProviderModelCandidateView confirm(
            PlatformRequestContext context,
            @PathVariable String providerConnectionId,
            @RequestBody PlatformProviderModelCandidateConfirmationRequest request) {
        return confirmations.confirm(new ConfirmPlatformProviderModelCandidateCommand(
                context, providerConnectionId, request.expectedConnectionVersion(),
                request.modelCode(), request.modelType()));
    }
}
