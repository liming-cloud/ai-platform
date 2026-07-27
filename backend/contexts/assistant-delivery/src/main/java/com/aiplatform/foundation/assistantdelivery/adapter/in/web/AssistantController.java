package com.aiplatform.foundation.assistantdelivery.adapter.in.web;

import com.aiplatform.foundation.assistantdelivery.api.AssistantUseCase;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.AssistantPage;
import com.aiplatform.foundation.assistantdelivery.api.model.version.AssistantVersionView;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.AssistantView;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.version.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.api.model.assistant.ListAssistantsQuery;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
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

/**
 * 助手创建、版本冻结与发布入口。
 */
@RestController
@RequestMapping("/api/v1/assistants")
@ConditionalOnBean(AssistantUseCase.class)
public class AssistantController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final AssistantUseCase assistants;

    public AssistantController(AssistantUseCase assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手用例不能为空");
    }

    @PostMapping
    public ResponseEntity<AssistantView> create(
            TrustedRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody AssistantRequest request) {
        var result = assistants.create(new CreateAssistantCommand(
                context,
                requireIdempotencyKey(idempotencyKey),
                request.displayName()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.assistant());
    }

    @GetMapping
    public AssistantPage list(
            TrustedRequestContext context,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return assistants.list(new ListAssistantsQuery(context, cursor, limit));
    }

    @PostMapping("/{assistantId}/versions")
    public ResponseEntity<AssistantVersionView> createVersion(
            TrustedRequestContext context,
            @PathVariable String assistantId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody AssistantVersionRequest request) {
        var result = assistants.createVersion(new CreateAssistantVersionCommand(
                context,
                requireIdempotencyKey(idempotencyKey),
                assistantId,
                request.chatDeploymentId(),
                request.knowledgeSpaceId(),
                request.systemInstruction(),
                request.maxOutputTokens(),
                request.retrievalLimit(),
                request.minimumScore(),
                request.citationPolicy(),
                request.refusalPolicy()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.assistantVersion());
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }
}
