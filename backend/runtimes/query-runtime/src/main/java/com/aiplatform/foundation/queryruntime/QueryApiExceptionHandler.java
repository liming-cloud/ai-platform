package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.organizationaccess.adapter.out.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.trustedquery.application.CandidateSearchPermissionDeniedException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 在线查询错误归一化，不泄漏租户、会话和数据库事实。
 */
@RestControllerAdvice
public class QueryApiExceptionHandler {

    @ExceptionHandler(InvalidAuthenticatedSessionException.class)
    ResponseEntity<QueryApiError> invalidSession() {
        return error(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "需要有效会话");
    }

    @ExceptionHandler(CandidateSearchPermissionDeniedException.class)
    ResponseEntity<QueryApiError> accessDenied() {
        return error(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "没有执行该操作的权限");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<QueryApiError> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式不正确");
    }

    private static ResponseEntity<QueryApiError> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(new QueryApiError(
                code,
                message,
                UUID.randomUUID().toString(),
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
