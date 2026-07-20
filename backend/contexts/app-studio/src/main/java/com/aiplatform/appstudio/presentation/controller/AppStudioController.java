package com.aiplatform.appstudio.presentation.controller;

import com.aiplatform.appstudio.application.command.CreateAiApplicationCommand;
import com.aiplatform.appstudio.application.command.CreateChatSessionCommand;
import com.aiplatform.appstudio.application.command.GetDebugEvidenceCommand;
import com.aiplatform.appstudio.application.command.PublishAiApplicationCommand;
import com.aiplatform.appstudio.application.command.ReplaceApplicationAccessPolicyCommand;
import com.aiplatform.appstudio.application.command.RollbackApplicationReleaseCommand;
import com.aiplatform.appstudio.application.command.RunPublishPreflightCommand;
import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.command.UpdateAiApplicationCommand;
import com.aiplatform.appstudio.application.service.AppStudioApplicationService;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.debug.DebugEvidenceView;
import com.aiplatform.appstudio.presentation.request.CreateAiApplicationRequest;
import com.aiplatform.appstudio.presentation.request.CreateChatSessionRequest;
import com.aiplatform.appstudio.presentation.request.PublishAiApplicationRequest;
import com.aiplatform.appstudio.presentation.request.ReplaceApplicationAccessPolicyRequest;
import com.aiplatform.appstudio.presentation.request.RollbackApplicationReleaseRequest;
import com.aiplatform.appstudio.presentation.request.RunPublishPreflightRequest;
import com.aiplatform.appstudio.presentation.request.SendChatMessageRequest;
import com.aiplatform.appstudio.presentation.request.UpdateAiApplicationRequest;
import com.aiplatform.appstudio.presentation.response.AiApplicationCatalogResponse;
import com.aiplatform.appstudio.presentation.response.AiApplicationDetailResponse;
import com.aiplatform.appstudio.presentation.response.ApplicationAccessPolicyResponse;
import com.aiplatform.appstudio.presentation.response.ApplicationReleaseDetailResponse;
import com.aiplatform.appstudio.presentation.response.ApplicationReleaseSummaryResponse;
import com.aiplatform.appstudio.presentation.response.AppStudioDebugRunDetailResponse;
import com.aiplatform.appstudio.presentation.response.AppStudioDebugRunSummaryResponse;
import com.aiplatform.appstudio.presentation.response.ChatMessageHistoryResponse;
import com.aiplatform.appstudio.presentation.response.ChatSessionSummaryResponse;
import com.aiplatform.appstudio.presentation.response.CreateAiApplicationResponse;
import com.aiplatform.appstudio.presentation.response.CreateChatSessionResponse;
import com.aiplatform.appstudio.presentation.response.PublishPreflightResponse;
import com.aiplatform.appstudio.presentation.response.SendChatMessageResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AppStudioController {
    private final AppStudioApplicationService service;
    private final TenantFeatureGuard featureGuard;

    public AppStudioController(AppStudioApplicationService service, TenantFeatureGuard featureGuard) {
        this.service = Objects.requireNonNull(service, "App Studio 应用服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @GetMapping("/ai-applications")
    public ApiResponse<List<AiApplicationCatalogResponse>> listApplications() {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(service.listApplications(
                        tenantId,
                        PrincipalContext.currentActorId())
                .stream()
                .map(AiApplicationCatalogResponse::from)
                .toList());
    }

    @PostMapping("/ai-applications")
    public ApiResponse<CreateAiApplicationResponse> createApplication(@Valid @RequestBody CreateAiApplicationRequest request) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(CreateAiApplicationResponse.from(service.createApplication(new CreateAiApplicationCommand(
                tenantId,
                PrincipalContext.currentActorId(),
                AiApplicationType.valueOf(request.applicationType().trim().toUpperCase()),
                request.name(),
                request.description(),
                request.modelConfigId(),
                request.knowledgeBaseIds(),
                request.systemPrompt()
        ))));
    }

    @PutMapping("/ai-applications/{applicationId}")
    public ApiResponse<AiApplicationDetailResponse> updateApplication(
            @PathVariable String applicationId,
            @Valid @RequestBody UpdateAiApplicationRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(AiApplicationDetailResponse.from(service.updateApplication(new UpdateAiApplicationCommand(
                tenantId,
                PrincipalContext.currentActorId(),
                applicationId,
                request.name(),
                request.description(),
                request.modelConfigId(),
                request.knowledgeBaseIds(),
                request.systemPrompt()
        ))));
    }

    @PutMapping("/ai-applications/{applicationId}/access-policy")
    public ApiResponse<ApplicationAccessPolicyResponse> replaceApplicationAccessPolicy(
            @PathVariable String applicationId,
            @Valid @RequestBody ReplaceApplicationAccessPolicyRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(ApplicationAccessPolicyResponse.from(
                service.replaceApplicationAccessPolicy(new ReplaceApplicationAccessPolicyCommand(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId,
                        request.userIds(),
                        request.roleIds(),
                        request.reason()
                ))
        ));
    }

    @PostMapping("/ai-applications/{applicationId}/publish")
    public ApiResponse<AiApplicationDetailResponse> publishApplication(
            @PathVariable String applicationId,
            @Valid @RequestBody(required = false) PublishAiApplicationRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        PublishAiApplicationCommand command = request == null
                ? new PublishAiApplicationCommand(tenantId, PrincipalContext.currentActorId(), applicationId)
                : new PublishAiApplicationCommand(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId,
                        request.publishRequestId(),
                        request.reason()
                );
        return ApiResponse.success(AiApplicationDetailResponse.from(service.publishApplication(command)));
    }

    public ApiResponse<AiApplicationDetailResponse> publishApplication(String applicationId) {
        return publishApplication(applicationId, null);
    }

    @GetMapping("/ai-applications/{applicationId}/releases")
    public ApiResponse<List<ApplicationReleaseSummaryResponse>> listApplicationReleases(
            @PathVariable String applicationId
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(service.listApplicationReleases(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId
                ).stream()
                .map(ApplicationReleaseSummaryResponse::from)
                .toList());
    }

    @GetMapping("/ai-applications/{applicationId}/releases/{releaseId}")
    public ApiResponse<ApplicationReleaseDetailResponse> getApplicationRelease(
            @PathVariable String applicationId,
            @PathVariable String releaseId
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(ApplicationReleaseDetailResponse.from(service.getApplicationRelease(
                tenantId,
                PrincipalContext.currentActorId(),
                applicationId,
                releaseId
        )));
    }

    @PostMapping("/ai-applications/{applicationId}/releases/{releaseId}/rollback")
    public ApiResponse<AiApplicationDetailResponse> rollbackApplicationRelease(
            @PathVariable String applicationId,
            @PathVariable String releaseId,
            @Valid @RequestBody RollbackApplicationReleaseRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(AiApplicationDetailResponse.from(service.rollbackApplication(
                new RollbackApplicationReleaseCommand(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId,
                        releaseId,
                        request.rollbackRequestId(),
                        request.reason()
                )
        )));
    }

    @PostMapping("/ai-applications/{applicationId}/publish/preflight")
    public ApiResponse<PublishPreflightResponse> runPublishPreflight(
            @PathVariable String applicationId,
            @Valid @RequestBody RunPublishPreflightRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(PublishPreflightResponse.from(service.runPublishPreflight(
                new RunPublishPreflightCommand(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId,
                        request.publishRequestId()
                )
        )));
    }

    @PostMapping("/ai-applications/{applicationId}/chat-sessions")
    public ApiResponse<CreateChatSessionResponse> createSession(
            @PathVariable String applicationId,
            @RequestBody(required = false) CreateChatSessionRequest request
    ) {
        CreateChatSessionRequest safeRequest = request == null ? new CreateChatSessionRequest(null, null) : request;
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(CreateChatSessionResponse.from(service.createSession(new CreateChatSessionCommand(
                tenantId,
                PrincipalContext.currentActorId(),
                applicationId,
                safeRequest.title(),
                safeRequest.traceId()
        ))));
    }

    @GetMapping("/ai-applications/{applicationId}/chat-sessions")
    public ApiResponse<List<ChatSessionSummaryResponse>> listSessions(@PathVariable String applicationId) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(service.listSessions(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId)
                .stream()
                .map(ChatSessionSummaryResponse::from)
                .toList());
    }

    @GetMapping("/chat-sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageHistoryResponse>> listMessages(@PathVariable String sessionId) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(service.listMessages(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        sessionId)
                .stream()
                .map(ChatMessageHistoryResponse::from)
                .toList());
    }

    @GetMapping("/ai-applications/{applicationId}/debug-runs")
    public ApiResponse<List<AppStudioDebugRunSummaryResponse>> listDebugRuns(
            @PathVariable String applicationId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String modelInvocationLogId,
            @RequestParam(required = false) String invocationStatus
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(service.listDebugRuns(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        applicationId,
                        sessionId,
                        traceId,
                        modelInvocationLogId,
                        invocationStatus)
                .stream()
                .map(AppStudioDebugRunSummaryResponse::from)
                .toList());
    }

    @GetMapping("/ai-applications/{applicationId}/debug-runs/{assistantMessageId}")
    public ApiResponse<AppStudioDebugRunDetailResponse> getDebugRunDetail(
            @PathVariable String applicationId,
            @PathVariable String assistantMessageId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String reason
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(AppStudioDebugRunDetailResponse.from(service.getDebugEvidence(
                new GetDebugEvidenceCommand(
                tenantId,
                PrincipalContext.currentActorId(),
                applicationId,
                assistantMessageId,
                DebugEvidenceView.from(view),
                reason
        ))));
    }

    @PostMapping("/chat-sessions/{sessionId}/messages")
    public ApiResponse<SendChatMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        SendChatMessageCommand command = request.clientMessageId() == null
                ? new SendChatMessageCommand(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        sessionId,
                        request.effectiveContent(),
                        request.traceId()
                )
                : new SendChatMessageCommand(
                        tenantId,
                        PrincipalContext.currentActorId(),
                        sessionId,
                        request.clientMessageId(),
                        request.effectiveContent(),
                        request.traceId()
                );
        return ApiResponse.success(SendChatMessageResponse.from(service.sendMessage(command)));
    }

    private TenantId requireAppStudioFeature() {
        TenantId tenantId = TenantContext.current();
        featureGuard.requireEnabled(tenantId, TenantFeatureCodes.APP_STUDIO);
        return tenantId;
    }
}
