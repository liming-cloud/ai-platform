package com.aiplatform.appstudio.presentation.controller;

import com.aiplatform.appstudio.application.command.SubmitChatMessageFeedbackCommand;
import com.aiplatform.appstudio.application.service.ChatMessageFeedbackApplicationService;
import com.aiplatform.appstudio.presentation.request.SubmitChatMessageFeedbackRequest;
import com.aiplatform.appstudio.presentation.response.ChatMessageFeedbackResponse;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureCodes;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.web.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat-sessions/{sessionId}/messages/{assistantMessageId}/feedback")
public class ChatMessageFeedbackController {
    private final ChatMessageFeedbackApplicationService service;
    private final TenantFeatureGuard featureGuard;

    public ChatMessageFeedbackController(ChatMessageFeedbackApplicationService service, TenantFeatureGuard featureGuard) {
        this.service = Objects.requireNonNull(service, "消息反馈应用服务不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
    }

    @PostMapping
    public ApiResponse<ChatMessageFeedbackResponse> submit(
            @PathVariable String sessionId,
            @PathVariable String assistantMessageId,
            @Valid @RequestBody SubmitChatMessageFeedbackRequest request
    ) {
        TenantId tenantId = TenantContext.current();
        featureGuard.requireEnabled(tenantId, TenantFeatureCodes.APP_STUDIO);
        return ApiResponse.success(ChatMessageFeedbackResponse.from(service.submit(new SubmitChatMessageFeedbackCommand(
                tenantId, PrincipalContext.currentActorId(), sessionId, assistantMessageId, request.rating(),
                request.labels() == null ? List.of() : request.labels(), request.comment()
        ))));
    }
}
