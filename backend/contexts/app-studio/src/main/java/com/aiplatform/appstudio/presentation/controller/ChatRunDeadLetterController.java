package com.aiplatform.appstudio.presentation.controller;

import com.aiplatform.appstudio.application.command.ReplayChatRunDeadLetterCommand;
import com.aiplatform.appstudio.application.service.ChatRunDeadLetterApplicationService;
import com.aiplatform.appstudio.presentation.request.ReplayChatRunDeadLetterRequest;
import com.aiplatform.appstudio.presentation.response.ChatRunDeadLetterResponse;
import com.aiplatform.appstudio.presentation.response.ChatRunManualReplayResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat-runs")
public class ChatRunDeadLetterController {
    private final ChatRunDeadLetterApplicationService service;
    private final TenantFeatureGuard featureGuard;

    public ChatRunDeadLetterController(
            ChatRunDeadLetterApplicationService service,
            TenantFeatureGuard featureGuard
    ) {
        this.service = Objects.requireNonNull(service, "聊天最终化死信服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @GetMapping("/dead-letters")
    public ApiResponse<List<ChatRunDeadLetterResponse>> listDeadLetters(
            @RequestParam(defaultValue = "20") int limit
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(service.listDeadLetters(
                        tenantId, PrincipalContext.currentActorId(), limit
                ).stream()
                .map(ChatRunDeadLetterResponse::from)
                .toList());
    }

    @PostMapping("/{runId}/dead-letter/replay")
    public ApiResponse<ChatRunManualReplayResponse> replay(
            @PathVariable String runId,
            @Valid @RequestBody ReplayChatRunDeadLetterRequest request
    ) {
        TenantId tenantId = requireAppStudioFeature();
        return ApiResponse.success(ChatRunManualReplayResponse.from(service.replay(
                new ReplayChatRunDeadLetterCommand(
                        tenantId,
                        runId,
                        PrincipalContext.currentActorId(),
                        request.replayRequestId(),
                        request.reason(),
                        TraceContext.currentTraceId()
                )
        )));
    }

    private TenantId requireAppStudioFeature() {
        TenantId tenantId = TenantContext.current();
        featureGuard.requireEnabled(tenantId, TenantFeatureCodes.APP_STUDIO);
        return tenantId;
    }
}
