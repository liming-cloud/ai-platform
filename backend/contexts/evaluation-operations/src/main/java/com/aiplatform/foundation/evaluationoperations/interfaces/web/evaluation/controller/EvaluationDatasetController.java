package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationRequest;
import com.aiplatform.foundation.evaluationoperations.application.evaluation.service.EvaluationDatasetRegistrationService;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.IOException;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 私有合成评测数据集登记入口；身份、租户和权限一律由 `TrustedRequestContext` 从会话恢复。
 */
@RequiresTenantFeature("evaluationGovernance")
@RestController
@ConditionalOnBean(EvaluationDatasetRegistrationService.class)
@RequestMapping("/api/v1/evaluation-datasets")
public class EvaluationDatasetController {

    private final EvaluationDatasetRegistrationService registrations;

    public EvaluationDatasetController(EvaluationDatasetRegistrationService registrations) {
        this.registrations = Objects.requireNonNull(registrations, "评测数据集登记用例不能为空");
    }

    /**
     * 仅接收 JSON Lines 正文与保留策略；摘要、条目数、内容分类和私有对象位置必须由服务端生成。
     */
    @PostMapping
    public ResponseEntity<EvaluationDatasetRegistrationView> register(
            TrustedRequestContext context,
            @RequestParam MultipartFile content,
            @RequestParam String retentionPolicyId) {
        try {
            var dataset = registrations.register(
                    context,
                    new EvaluationDatasetRegistrationRequest(
                            content.getInputStream(), retentionPolicyId));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(EvaluationDatasetRegistrationView.from(dataset));
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取评测数据集正文失败", exception);
        }
    }
}
