package com.aiplatform.foundation.controlplane.web.knowledge;

import com.aiplatform.foundation.knowledgedata.api.TextDataSourceUseCase;
import com.aiplatform.foundation.knowledgedata.api.model.CreateTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.ListTextDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourcePage;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourceView;
import com.aiplatform.foundation.knowledgedata.api.model.UploadTextContentCommand;
import com.aiplatform.foundation.knowledgedata.application.content.TextContentProcessingException;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
            @RequestParam String knowledgeSpaceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return dataSources.list(new ListTextDataSourcesQuery(
                context,
                knowledgeSpaceId,
                cursor,
                limit));
    }

    @PostMapping(
            value = "/{dataSourceId}/content",
            consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<TextDataSourceView> upload(
            TrustedRequestContext context,
            @PathVariable String dataSourceId,
            @RequestParam String knowledgeSpaceId,
            @RequestParam long expectedVersion,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String mediaType,
            HttpServletRequest request) {
        try {
            var result = dataSources.upload(new UploadTextContentCommand(
                    context,
                    knowledgeSpaceId,
                    dataSourceId,
                    expectedVersion,
                    requireIdempotencyKey(idempotencyKey),
                    requireUtf8TextMediaType(mediaType),
                    request.getInputStream()));
            return ResponseEntity
                    .ok()
                    .header(IDEMPOTENCY_REPLAYED, Boolean.toString(result.replayed()))
                    .body(result.dataSource());
        } catch (IOException exception) {
            throw new TextContentProcessingException(exception);
        }
    }

    @PostMapping("/{dataSourceId}/publish")
    public ResponseEntity<TextDataSourceView> publish(
            TrustedRequestContext context,
            @PathVariable String dataSourceId,
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

    private static String requireIdentifier(String value) {
        if (value == null || value.isBlank()) {
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

    private static String requireUtf8TextMediaType(String value) {
        if (value == null) {
            throw new IllegalArgumentException("必须提供媒体类型");
        }
        var parsed = MediaType.parseMediaType(value);
        if (!MediaType.TEXT_PLAIN.includes(parsed)
                || !StandardCharsets.UTF_8.equals(parsed.getCharset())
                || parsed.getParameters().size() != 1) {
            throw new IllegalArgumentException("只支持 UTF-8 纯文本");
        }
        return TextContentReference.UTF8_TEXT;
    }
}
