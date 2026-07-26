package com.aiplatform.foundation.controlplane.web.modelcatalog;

import com.aiplatform.foundation.modelruntime.api.catalog.ModelDeploymentUseCase;
import com.aiplatform.foundation.modelruntime.api.catalog.model.CreateModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ListModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ModelDeploymentView;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.api.catalog.model.ProbeModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.api.catalog.model.PublishModelDeploymentCommand;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
 * 模型部署创建、列表、探测和发布入口。
 */
@RestController
@RequestMapping("/api/v1/model-deployments")
@ConditionalOnBean(ModelDeploymentUseCase.class)
public class ModelDeploymentController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final ModelDeploymentUseCase modelDeployments;

    public ModelDeploymentController(ModelDeploymentUseCase modelDeployments) {
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
    public ModelDeploymentPage list(
            TrustedRequestContext context,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return modelDeployments.list(new ListModelDeploymentsQuery(
                context,
                cursor,
                limit));
    }

    @PostMapping("/{modelDeploymentId}/probe")
    public ProbeModelDeploymentResult probe(
            TrustedRequestContext context,
            @PathVariable UUID modelDeploymentId,
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
            @PathVariable UUID modelDeploymentId,
            @RequestBody ModelDeploymentPublishRequest request) {
        return modelDeployments.publish(new PublishModelDeploymentCommand(
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
