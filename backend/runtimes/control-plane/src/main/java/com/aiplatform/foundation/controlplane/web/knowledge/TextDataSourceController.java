package com.aiplatform.foundation.controlplane.web.knowledge;

import com.aiplatform.foundation.knowledgedata.api.TextDataSourceUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.ListTextDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourcePage;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourceView;
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
 * 文本数据源登记、列表和发布入口。
 */
@RestController
@RequestMapping("/api/v1/data-sources")
@ConditionalOnBean(TextDataSourceUseCase.class)
public class TextDataSourceController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    private final TextDataSourceUseCase dataSources;

    public TextDataSourceController(TextDataSourceUseCase dataSources) {
        this.dataSources = Objects.requireNonNull(
                dataSources,
                "文本数据源用例不能为空");
    }

    @PostMapping
    public ResponseEntity<TextDataSourceView> create(
            TrustedRequestContext context,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody TextDataSourceRequest request) {
        requireRequest(request);
        var result = dataSources.create(new CreateTextDataSourceCommand(
                context,
                requireIdentifier(request.knowledgeSpaceId()),
                requireIdempotencyKey(idempotencyKey),
                requireText(request.name())));
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.dataSource());
    }

    @GetMapping
    public TextDataSourcePage list(
            TrustedRequestContext context,
            @RequestParam UUID knowledgeSpaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return dataSources.list(new ListTextDataSourcesQuery(
                context,
                knowledgeSpaceId,
                cursor,
                limit));
    }

    @PostMapping("/{dataSourceId}/publish")
    public ResponseEntity<TextDataSourceView> publish(
            TrustedRequestContext context,
            @PathVariable UUID dataSourceId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody PublishTextDataSourceRequest request) {
        requireRequest(request);
        if (request.expectedVersion() == null) {
            throw new IllegalArgumentException("必须提供预期版本");
        }
        var result = dataSources.publish(new PublishTextDataSourceCommand(
                context,
                requireIdentifier(request.knowledgeSpaceId()),
                dataSourceId,
                request.expectedVersion(),
                requireIdempotencyKey(idempotencyKey)));
        return ResponseEntity
                .ok()
                .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                .body(result.dataSource());
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
    }

    private static UUID requireIdentifier(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供知识空间标识");
        }
        return value;
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供幂等键");
        }
        return value;
    }

    private static String requireText(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供数据源名称");
        }
        return value;
    }
}
