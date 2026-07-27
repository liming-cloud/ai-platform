package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.QueryRunEventUseCase;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.api.model.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.api.model.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunView;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 创建和读取可信查询运行的在线入口。
 */
@RestController
@RequestMapping("/api/v1/query-runs")
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
public class QueryRunController {

    private final QueryRunUseCase queryRuns;
    private final QueryRunEventUseCase queryEvents;

    public QueryRunController(
            QueryRunUseCase queryRuns,
            QueryRunEventUseCase queryEvents) {
        this.queryRuns = Objects.requireNonNull(queryRuns, "查询运行用例不能为空");
        this.queryEvents = Objects.requireNonNull(
                queryEvents,
                "查询事件用例不能为空");
    }

    @PostMapping
    public ResponseEntity<QueryRunView> create(
            TrustedRequestContext context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody QueryRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        var result = queryRuns.create(new CreateQueryRunCommand(
                context,
                idempotencyKey,
                request.assistantVersionId(),
                request.question()));
        return ResponseEntity
                .created(URI.create(
                        "/api/v1/query-runs/" + result.queryRun().id()))
                .header(
                        "Idempotency-Replayed",
                        Boolean.toString(result.replayed()))
                .body(result.queryRun());
    }

    @GetMapping("/{queryRunId}")
    public QueryRunView get(
            TrustedRequestContext context,
            @PathVariable String queryRunId) {
        return queryRuns.get(new GetQueryRunQuery(context, queryRunId));
    }

    @GetMapping(
            path = "/{queryRunId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            TrustedRequestContext context,
            @PathVariable String queryRunId,
            @RequestHeader(name = "Last-Event-ID", required = false)
            String lastEventId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        var resumeAfter = resumeAfter(lastEventId, afterSequence);
        var page = queryEvents.list(new ListQueryRunEventsQuery(
                context,
                queryRunId,
                resumeAfter,
                100));
        var emitter = new SseEmitter(30_000L);
        try {
            for (var event : page.items()) {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(event.sequence()))
                        .name(event.type())
                        .data(event));
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /**
     * 查询正文只包含已发布助手版本和问题，不接受租户或模型覆盖参数。
     */
    public record QueryRunRequest(
            String assistantVersionId,
            String question) {
    }

    private static long resumeAfter(String lastEventId, long afterSequence) {
        if (afterSequence < 0) {
            throw new IllegalArgumentException("事件序号不能为负数");
        }
        if (lastEventId == null) {
            return afterSequence;
        }
        final long parsed;
        try {
            parsed = Long.parseLong(lastEventId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("最后事件序号格式不正确", exception);
        }
        if (parsed < 0 || afterSequence > 0 && parsed != afterSequence) {
            throw new IllegalArgumentException("事件恢复游标不一致");
        }
        return parsed;
    }
}
