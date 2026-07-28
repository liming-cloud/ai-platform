package com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.controller;

import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.AssistantVersionView;
import com.aiplatform.foundation.assistantdelivery.application.assistant.dto.AssistantView;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.CreateAssistantCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.command.CreateAssistantVersionCommand;
import com.aiplatform.foundation.assistantdelivery.application.assistant.query.ListAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.assistant.service.AssistantService;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request.AssistantRequest;
import com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request.AssistantVersionRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
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
@ConditionalOnBean(AssistantService.class)
public class AssistantController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final AssistantService assistants;

    public AssistantController(AssistantService assistants) {
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
    public PageResult<AssistantView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return assistants.list(new ListAssistantsQuery(
                context,
                new PageQuery(pageNumber, pageSize)));
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
                request.chatModelConfigurationId(),
                request.embeddingModelConfigurationId(),
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
