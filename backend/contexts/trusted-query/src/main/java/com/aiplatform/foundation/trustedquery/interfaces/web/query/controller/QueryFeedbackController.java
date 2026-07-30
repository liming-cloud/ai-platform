package com.aiplatform.foundation.trustedquery.interfaces.web.query.controller;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.application.feedback.command.RecordQueryFeedbackCommand;
import com.aiplatform.foundation.trustedquery.application.feedback.service.QueryFeedbackService;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackOutcome;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedbackReason;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已完成查询的最小反馈在线入口。
 */
@RestController
@RequestMapping("/api/v1/query-runs")
@ConditionalOnProperty(name = "foundation.query.enabled", havingValue = "true")
public class QueryFeedbackController {

    private final QueryFeedbackService feedbacks;

    public QueryFeedbackController(QueryFeedbackService feedbacks) {
        this.feedbacks = Objects.requireNonNull(feedbacks, "查询反馈用例不能为空");
    }

    /**
     * 身份仅由令牌解析的可信上下文提供，请求体不得携带租户或成员标识。
     */
    @PostMapping("/{queryRunId}/feedback")
    public ResponseEntity<Void> record(
            TrustedRequestContext context,
            @PathVariable String queryRunId,
            @RequestBody QueryFeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        feedbacks.record(new RecordQueryFeedbackCommand(
                context,
                queryRunId,
                request.outcome(),
                request.reason()));
        return ResponseEntity.noContent().build();
    }

    /**
     * 反馈正文只允许正负结果与受控原因枚举。
     */
    public record QueryFeedbackRequest(
            QueryFeedbackOutcome outcome,
            QueryFeedbackReason reason) {
    }
}
