package com.aiplatform.foundation.controlplane.web.assistant;

import com.aiplatform.foundation.assistantdelivery.api.AssistantUseCase;
import com.aiplatform.foundation.assistantdelivery.api.model.AssistantVersionView;
import com.aiplatform.foundation.assistantdelivery.api.model.PublishAssistantVersionCommand;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 助手不可变版本的独立发布入口。
 */
@RestController
@RequestMapping("/api/v1/assistant-versions")
@ConditionalOnBean(AssistantUseCase.class)
public class AssistantVersionController {

    private final AssistantUseCase assistants;

    public AssistantVersionController(AssistantUseCase assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手用例不能为空");
    }

    @PostMapping("/{assistantVersionId}/publish")
    public ResponseEntity<AssistantVersionView> publishVersion(
            TrustedRequestContext context,
            @PathVariable String assistantVersionId,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestBody AssistantVersionPublishRequest request) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        var result = assistants.publishVersion(new PublishAssistantVersionCommand(
                context,
                idempotencyKey,
                assistantVersionId,
                request.expectedVersion()));
        return ResponseEntity
                .ok()
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.assistantVersion());
    }
}
