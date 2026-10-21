package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DisablePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformModelDeploymentView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.ProbePlatformModelDeploymentResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformModelDeploymentsQuery;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformModelDeploymentService;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformModelDeploymentDisableRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformModelDeploymentPublishRequest;
import com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformModelDeploymentRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Duration;
import java.util.Objects;
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

/** 平台范围模型部署的创建、分页、探测与发布入口。 */
@RestController
@RequestMapping("/api/v1/platform/model-supply/deployments")
@ConditionalOnBean(PlatformModelDeploymentService.class)
public class PlatformModelDeploymentController {
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";
    private final PlatformModelDeploymentService deployments;

    public PlatformModelDeploymentController(PlatformModelDeploymentService deployments) {
        this.deployments = Objects.requireNonNull(deployments, "平台模型部署用例不能为空");
    }

    @PostMapping
    public ResponseEntity<PlatformModelDeploymentView> create(
            PlatformRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody PlatformModelDeploymentRequest request) {
        var result = deployments.create(new CreatePlatformModelDeploymentCommand(context,
                requireIdempotencyKey(idempotencyKey), request.providerConnectionId(), request.expectedConnectionVersion(),
                request.modelCode(), request.displayName(), request.modelType(), request.capabilities(), request.contextWindow(),
                request.outputLimit(), request.embeddingDimension(), request.rerankCandidateLimit(),
                request.parameterSource(), request.plannedTenantIds(),
                request.modelConfirmed() != null && request.modelConfirmed().isBoolean() && request.modelConfirmed().booleanValue()));
        return ResponseEntity.status(HttpStatus.CREATED).header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.deployment());
    }

    @GetMapping
    public PageResult<PlatformModelDeploymentView> list(
            PlatformRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return deployments.list(new ListPlatformModelDeploymentsQuery(context, new PageQuery(pageNumber, pageSize)));
    }

    @GetMapping("/facts")
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeploymentPageFacts facts(PlatformRequestContext context) {
        return deployments.facts(context);
    }

    @GetMapping("/candidates")
    public PageResult<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeployableModel> options(
            PlatformRequestContext context, @RequestParam(defaultValue = "1") long pageNumber, @RequestParam(defaultValue = "100") long pageSize) {
        return deployments.options(context, new PageQuery(pageNumber, pageSize));
    }

    @GetMapping("/{modelDeploymentId}")
    public PlatformModelDeploymentView detail(PlatformRequestContext context, @PathVariable String modelDeploymentId) {
        return deployments.detail(context, modelDeploymentId);
    }

    @PostMapping("/{modelDeploymentId}/probe")
    public ProbePlatformModelDeploymentResult probe(
            PlatformRequestContext context, @PathVariable String modelDeploymentId,
            @RequestBody PlatformModelDeploymentProbeRequest request,
            org.springframework.web.context.request.NativeWebRequest httpRequest) {
        var continuity = httpRequest.getAttribute(
                com.aiplatform.foundation.security.session.PlatformSessionContinuity.REQUEST_ATTRIBUTE,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        if (!(continuity instanceof com.aiplatform.foundation.security.session.PlatformSessionContinuity session)
                || !session.matches(context))
            throw new com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException();
        return deployments.probe(new ProbePlatformModelDeploymentCommand(context, modelDeploymentId,
                request.expectedVersion(), Duration.ofMillis(request.timeoutMillis()), session));
    }

    @PostMapping("/{modelDeploymentId}/publish")
    public PlatformModelDeploymentView publish(
            PlatformRequestContext context, @PathVariable String modelDeploymentId,
            @RequestBody PlatformModelDeploymentPublishRequest request,
            org.springframework.web.context.request.NativeWebRequest httpRequest) {
        return deployments.publish(new PublishPlatformModelDeploymentCommand(context, modelDeploymentId,
                request.expectedVersion(), continuity(context, httpRequest)));
    }

    @PostMapping("/{modelDeploymentId}/disable")
    public PlatformModelDeploymentView disable(
            PlatformRequestContext context, @PathVariable String modelDeploymentId,
            @RequestBody PlatformModelDeploymentDisableRequest request,
            org.springframework.web.context.request.NativeWebRequest httpRequest) {
        return deployments.disable(new DisablePlatformModelDeploymentCommand(context, modelDeploymentId,
                request.expectedVersion(), continuity(context, httpRequest)));
    }

    /** 当前对象的独立发布和授权影响，读取不调用厂商或改变配置。 */
    @GetMapping("/{modelDeploymentId}/supply")
    public com.aiplatform.foundation.modelruntime.application.platformsupply.dto.publication.PlatformSupplyFacts supply(
            PlatformRequestContext context, @PathVariable String modelDeploymentId) {
        return deployments.supplyFacts(context, modelDeploymentId).orElseThrow(
                com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNotFoundException::new);
    }

    /** 只收紧已选租户的供应权；权限来自平台会话，目标不能创建新授权。 */
    @PostMapping("/{modelDeploymentId}/grants/{tenantId}/revoke")
    public ResponseEntity<Void> revoke(PlatformRequestContext context, @PathVariable String modelDeploymentId,
            @PathVariable long tenantId,
            @RequestBody com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request.PlatformSupplyRevokeRequest request,
            org.springframework.web.context.request.NativeWebRequest httpRequest) {
        deployments.revokeSupply(context, modelDeploymentId, tenantId, request.deploymentVersion(), request.grantVersion(),
                continuity(context, httpRequest));
        return ResponseEntity.noContent().build();
    }

    private static com.aiplatform.foundation.security.session.PlatformSessionContinuity continuity(PlatformRequestContext context,
            org.springframework.web.context.request.NativeWebRequest request) {
        var value = request.getAttribute(com.aiplatform.foundation.security.session.PlatformSessionContinuity.REQUEST_ATTRIBUTE,
                org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof com.aiplatform.foundation.security.session.PlatformSessionContinuity session) || !session.matches(context))
            throw new com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException();
        return session;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) throw new IllegalArgumentException("必须提供幂等键");
        return value;
    }
}
