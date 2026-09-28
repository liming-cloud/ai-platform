package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationRunExecutionRequest;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationRunExecutionService;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受限评测运行入口；请求不接受任何身份、租户、私有正文或模型路由字段。
 */
@RequiresTenantFeature("evaluationGovernance")
@RestController
@ConditionalOnBean(EvaluationRunExecutionService.class)
@RequestMapping("/api/v1/evaluation-runs")
public class EvaluationRunController {

    private final EvaluationRunExecutionService executions;

    public EvaluationRunController(EvaluationRunExecutionService executions) {
        this.executions = Objects.requireNonNull(executions, "评测运行用例不能为空");
    }

    /**
     * 触发一次同租户评测并返回其无正文终态事实。
     */
    @PostMapping
    public ResponseEntity<EvaluationRunView> execute(
            TrustedRequestContext context,
            @RequestBody EvaluationRunExecuteRequest request) {
        var run = executions.execute(
                context,
                new EvaluationRunExecutionRequest(request.datasetId(), request.assistantVersionId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(EvaluationRunView.from(run));
    }
}
