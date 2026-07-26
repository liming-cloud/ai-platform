package com.aiplatform.foundation.controlplane.web.knowledge;

import com.aiplatform.foundation.knowledgedata.api.KnowledgeSpaceUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.KnowledgeSpacePage;
import com.aiplatform.foundation.knowledgedata.api.model.KnowledgeSpaceView;
import com.aiplatform.foundation.knowledgedata.api.model.ListKnowledgeSpacesQuery;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识空间创建和列表入口。
 */
@RestController
@RequestMapping("/api/v1/knowledge-spaces")
@ConditionalOnBean(KnowledgeSpaceUseCase.class)
public class KnowledgeSpaceController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final KnowledgeSpaceUseCase knowledgeSpaces;

    public KnowledgeSpaceController(KnowledgeSpaceUseCase knowledgeSpaces) {
        this.knowledgeSpaces = Objects.requireNonNull(
                knowledgeSpaces,
                "知识空间用例不能为空");
    }

    @PostMapping
    public ResponseEntity<KnowledgeSpaceView> create(
            TrustedRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody KnowledgeSpaceRequest request) {
        var result = knowledgeSpaces.create(new CreateKnowledgeSpaceCommand(
                context,
                requireIdempotencyKey(idempotencyKey),
                request.name(),
                request.description()));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.knowledgeSpace());
    }

    @GetMapping
    public KnowledgeSpacePage list(
            TrustedRequestContext context,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return knowledgeSpaces.list(new ListKnowledgeSpacesQuery(context, cursor, limit));
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }
}
