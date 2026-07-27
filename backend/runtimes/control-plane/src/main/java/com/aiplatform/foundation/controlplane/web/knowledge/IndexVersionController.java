package com.aiplatform.foundation.controlplane.web.knowledge;

import com.aiplatform.foundation.knowledgedata.api.IndexVersionUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.api.model.IndexVersionView;
import com.aiplatform.foundation.knowledgedata.api.model.ListIndexVersionsQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishIndexVersionCommand;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
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
 * 知识空间索引版本的异步创建、查看和发布入口。
 */
@RestController
@RequestMapping("/api/v1/knowledge-spaces/{knowledgeSpaceId}/index-versions")
@ConditionalOnBean(IndexVersionUseCase.class)
public class IndexVersionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final IndexVersionUseCase indexVersions;

    public IndexVersionController(IndexVersionUseCase indexVersions) {
        this.indexVersions = Objects.requireNonNull(
                indexVersions,
                "索引版本用例不能为空");
    }

    @PostMapping
    public ResponseEntity<IndexVersionView> create(
            TrustedRequestContext context,
            @PathVariable String knowledgeSpaceId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
            String idempotencyKey,
            @RequestBody CreateIndexVersionRequest request) {
        if (request == null || request.embeddingDeploymentId() == null) {
            throw new IllegalArgumentException("必须选择嵌入部署");
        }
        var result = indexVersions.create(new CreateIndexVersionCommand(
                context,
                knowledgeSpaceId,
                request.embeddingDeploymentId(),
                requireIdempotencyKey(idempotencyKey)));
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.indexVersion());
    }

    @GetMapping
    public IndexVersionPage list(
            TrustedRequestContext context,
            @PathVariable String knowledgeSpaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return indexVersions.list(new ListIndexVersionsQuery(
                context,
                knowledgeSpaceId,
                cursor,
                limit));
    }

    @PostMapping("/{indexVersionId}/publish")
    public ResponseEntity<IndexVersionView> publish(
            TrustedRequestContext context,
            @PathVariable String knowledgeSpaceId,
            @PathVariable String indexVersionId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
            String idempotencyKey,
            @RequestBody PublishIndexVersionRequest request) {
        if (request == null || request.expectedVersion() == null) {
            throw new IllegalArgumentException("必须提供预期版本");
        }
        var result = indexVersions.publish(new PublishIndexVersionCommand(
                context,
                knowledgeSpaceId,
                indexVersionId,
                request.expectedVersion(),
                requireIdempotencyKey(idempotencyKey)));
        return ResponseEntity
                .ok()
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.indexVersion());
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }
}
