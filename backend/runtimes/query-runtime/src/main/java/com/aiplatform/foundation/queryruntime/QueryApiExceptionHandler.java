package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.assistantdelivery.application.AssistantPermissionDeniedException;
import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.kernel.identifier.TraceIds;
import com.aiplatform.foundation.organizationaccess.adapter.out.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.trustedquery.application.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.QueryAssistantUnavailableException;
import com.aiplatform.foundation.trustedquery.application.QueryRunConcurrentModificationException;
import com.aiplatform.foundation.trustedquery.application.QueryRunIdempotencyConflictException;
import com.aiplatform.foundation.trustedquery.application.QueryRunInProgressException;
import com.aiplatform.foundation.trustedquery.application.QueryRunNotFoundException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 在线查询错误归一化，不泄漏租户、会话和数据库事实。
 */
@RestControllerAdvice
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
public class QueryApiExceptionHandler {

    private final EntityIdGenerator identifiers;

    public QueryApiExceptionHandler(EntityIdGenerator identifiers) {
        this.identifiers = identifiers;
    }

    @ExceptionHandler(InvalidAuthenticatedSessionException.class)
    ResponseEntity<QueryApiError> invalidSession() {
        return error(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "需要有效会话");
    }

    @ExceptionHandler({
        AssistantPermissionDeniedException.class,
        CandidateSearchPermissionDeniedException.class
    })
    ResponseEntity<QueryApiError> accessDenied() {
        return error(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "没有执行该操作的权限");
    }

    @ExceptionHandler({
        QueryAssistantUnavailableException.class,
        QueryRunNotFoundException.class
    })
    ResponseEntity<QueryApiError> notFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求的资源不存在");
    }

    @ExceptionHandler(QueryRunInProgressException.class)
    ResponseEntity<QueryApiError> inProgress() {
        return error(HttpStatus.CONFLICT, "QUERY_IN_PROGRESS", "相同查询仍在处理中");
    }

    @ExceptionHandler(QueryQuotaExceededException.class)
    ResponseEntity<QueryApiError> quotaExceeded(QueryQuotaExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new QueryApiError(
                        "QUERY_QUOTA_EXCEEDED",
                        "查询配额已用尽",
                        TraceIds.next(identifiers),
                        Map.of(
                                "limitType", exception.limitType().name(),
                                "limit", exception.limit(),
                                "retryAt", exception.retryAt().toString())));
    }

    @ExceptionHandler({
        QueryRunConcurrentModificationException.class,
        QueryRunIdempotencyConflictException.class
    })
    ResponseEntity<QueryApiError> conflict() {
        return error(HttpStatus.CONFLICT, "REQUEST_CONFLICT", "请求状态已发生变化");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class
    })
    ResponseEntity<QueryApiError> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式不正确");
    }

    private ResponseEntity<QueryApiError> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(new QueryApiError(
                code,
                message,
                TraceIds.next(identifiers),
                Map.of()));
    }

    /**
     * 在线查询统一错误体。
     */
    public record QueryApiError(
            String code,
            String message,
            String traceId,
            Map<String, Object> details) {
    }
}
