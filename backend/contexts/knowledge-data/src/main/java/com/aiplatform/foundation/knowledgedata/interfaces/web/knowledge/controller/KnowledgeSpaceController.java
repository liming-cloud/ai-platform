package com.aiplatform.foundation.knowledgedata.interfaces.web.knowledge.controller;

import com.aiplatform.foundation.knowledgedata.application.knowledge.service.KnowledgeSpaceService;
import com.aiplatform.foundation.knowledgedata.application.knowledge.command.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.application.knowledge.dto.KnowledgeSpaceView;
import com.aiplatform.foundation.knowledgedata.application.knowledge.query.ListKnowledgeSpacesQuery;
import com.aiplatform.foundation.knowledgedata.interfaces.web.knowledge.request.KnowledgeSpaceRequest;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
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
public class KnowledgeSpaceController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final KnowledgeSpaceService knowledgeSpaces;

    public KnowledgeSpaceController(KnowledgeSpaceService knowledgeSpaces) {
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
    public PageResult<KnowledgeSpaceView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return knowledgeSpaces.list(new ListKnowledgeSpacesQuery(
                context,
                new PageQuery(pageNumber, pageSize)));
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }
}
