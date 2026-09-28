package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.dto.EvaluationDatasetCandidate;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationDatasetCandidateService;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 评测发起页读取当前租户已发布数据集安全摘要的入口。 */
@RequiresTenantFeature("evaluationGovernance")
@RestController
@ConditionalOnBean(EvaluationDatasetCandidateService.class)
@RequestMapping("/api/v1/evaluation-datasets/candidates")
public final class EvaluationDatasetCandidateController {

    private final EvaluationDatasetCandidateService service;

    public EvaluationDatasetCandidateController(EvaluationDatasetCandidateService service) {
        this.service = Objects.requireNonNull(service, "评测数据集候选服务不能为空");
    }

    @GetMapping
    public PageResult<EvaluationDatasetCandidate> page(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return service.page(context, new PageQuery(page, size));
    }
}
