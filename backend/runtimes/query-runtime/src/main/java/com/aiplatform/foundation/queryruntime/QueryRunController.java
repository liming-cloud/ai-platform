package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.QueryRunUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.api.model.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunView;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public QueryRunController(QueryRunUseCase queryRuns) {
        this.queryRuns = Objects.requireNonNull(queryRuns, "查询运行用例不能为空");
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
            @PathVariable UUID queryRunId) {
        return queryRuns.get(new GetQueryRunQuery(context, queryRunId));
    }

    /**
     * 查询正文只包含已发布助手版本和问题，不接受租户或模型覆盖参数。
     */
    public record QueryRunRequest(
            UUID assistantVersionId,
            String question) {
    }
}
