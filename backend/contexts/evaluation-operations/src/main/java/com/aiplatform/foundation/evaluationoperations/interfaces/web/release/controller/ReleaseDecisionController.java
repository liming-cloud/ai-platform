package com.aiplatform.foundation.evaluationoperations.interfaces.web.release.controller;

import com.aiplatform.foundation.evaluationoperations.application.release.service.ReleaseDecisionService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 从既有评测运行形成发布决定的无请求体入口；身份与租户仅从可信令牌上下文取得。
 */
@RestController
@ConditionalOnBean(ReleaseDecisionService.class)
@RequestMapping("/api/v1/evaluation-runs")
public class ReleaseDecisionController {

    private final ReleaseDecisionService decisions;

    public ReleaseDecisionController(ReleaseDecisionService decisions) {
        this.decisions = Objects.requireNonNull(decisions, "发布决定用例不能为空");
    }

    /**
     * 为一条同租户终态运行生成唯一的冻结发布决定。
     */
    @PostMapping("/{evaluationRunId}/release-decisions")
    public ResponseEntity<ReleaseDecisionView> decide(
            TrustedRequestContext context, @PathVariable long evaluationRunId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReleaseDecisionView.from(decisions.decide(context, evaluationRunId)));
    }
}
