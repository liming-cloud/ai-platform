package com.aiplatform.foundation.modelruntime.interfaces.web.deployment.controller;

import com.aiplatform.foundation.modelruntime.application.deployment.service.ModelDeploymentService;
import com.aiplatform.foundation.modelruntime.application.deployment.command.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.command.DisableModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.query.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.deployment.command.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.deployment.dto.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.deployment.command.PublishModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request.ModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request.ModelDeploymentDisableRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request.ModelDeploymentPublishRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.deployment.request.ModelDeploymentRequest;
import com.aiplatform.foundation.security.identity.RequiresTenantFeature;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型部署创建、列表、探测、发布和停用入口。
 */
@RequiresTenantFeature("modelGovernance")
@RestController
@RequestMapping("/api/v1/model-deployments")
public class ModelDeploymentController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final ModelDeploymentService modelDeployments;

    public ModelDeploymentController(ModelDeploymentService modelDeployments) {
        this.modelDeployments = Objects.requireNonNull(
                modelDeployments,
                "模型部署用例不能为空");
    }

    @PostMapping
    public ResponseEntity<ModelDeploymentView> create(
            TrustedRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody ModelDeploymentRequest request) {
        var result = modelDeployments.create(new CreateModelDeploymentCommand(
                context,
                requireIdempotencyKey(idempotencyKey),
                request.providerConnectionId(),
                request.modelCode(),
                request.displayName(),
                request.modelType(),
                request.capabilities(),
                request.contextWindow(),
                request.outputLimit(),
                request.embeddingDimension()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.modelDeployment());
    }

    @GetMapping
    public PageResult<ModelDeploymentView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return modelDeployments.list(new ListModelDeploymentsQuery(
                context,
                new PageQuery(pageNumber, pageSize)));
    }

    @PostMapping("/{modelDeploymentId}/probe")
    public ProbeModelDeploymentResult probe(
            TrustedRequestContext context,
            @PathVariable String modelDeploymentId,
            @RequestBody ModelDeploymentProbeRequest request) {
        return modelDeployments.probe(new ProbeModelDeploymentCommand(
                context,
                modelDeploymentId,
                request.expectedVersion(),
                Duration.ofMillis(request.timeoutMillis())));
    }

    @PostMapping("/{modelDeploymentId}/publish")
    public ModelDeploymentView publish(
            TrustedRequestContext context,
            @PathVariable String modelDeploymentId,
            @RequestBody ModelDeploymentPublishRequest request) {
        return modelDeployments.publish(new PublishModelDeploymentCommand(
                context,
                modelDeploymentId,
                request.expectedVersion()));
    }

    /**
     * 停用指定部署；身份和租户范围只来自可信请求上下文。
     */
    @PostMapping("/{modelDeploymentId}/disable")
    public ModelDeploymentView disable(
            TrustedRequestContext context,
            @PathVariable String modelDeploymentId,
            @RequestBody ModelDeploymentDisableRequest request) {
        return modelDeployments.disable(new DisableModelDeploymentCommand(
                context,
                modelDeploymentId,
                request.expectedVersion()));
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }
}
