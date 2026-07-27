package com.aiplatform.foundation.bootstrap.controlplane.web;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import com.aiplatform.foundation.assistantdelivery.application.AssistantConcurrentModificationException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantIdempotencyConflictException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantNameConflictException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantReferenceUnavailableException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantVersionNotFoundException;
import com.aiplatform.foundation.assistantdelivery.application.AssistantVersionQuotaExceededException;
import com.aiplatform.foundation.assistantdelivery.domain.AssistantVersionStateException;
import com.aiplatform.foundation.evaluationoperations.domain.authorization.OperationsPermissionDeniedException;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 把内部异常归一为不泄漏资源和凭据事实的公共错误。
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private final EntityIdGenerator identifiers;

    public GlobalApiExceptionHandler(EntityIdGenerator identifiers) {
        this.identifiers = identifiers;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "认证失败");
    }

    @ExceptionHandler(InvalidAuthenticatedSessionException.class)
    ResponseEntity<ApiErrorResponse> invalidSession() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "需要有效会话");
    }

    @ExceptionHandler(AssistantPermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> assistantAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
    }

    @ExceptionHandler(OperationsPermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> operationsAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
    }

    @ExceptionHandler(AssistantIdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> assistantIdempotencyConflict() {
        return error(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "幂等键已经用于不同请求");
    }

    @ExceptionHandler({
        AssistantNotFoundException.class,
        AssistantVersionNotFoundException.class
    })
    ResponseEntity<ApiErrorResponse> assistantResourceNotFound() {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "目标资源不存在");
    }

    @ExceptionHandler(AssistantNameConflictException.class)
    ResponseEntity<ApiErrorResponse> assistantNameConflict() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_NAME_CONFLICT",
                "当前租户已经存在同名助手");
    }

    @ExceptionHandler(AssistantConcurrentModificationException.class)
    ResponseEntity<ApiErrorResponse> assistantVersionConflict() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_VERSION_CONFLICT",
                "助手或助手版本状态已经变化");
    }

    @ExceptionHandler(AssistantReferenceUnavailableException.class)
    ResponseEntity<ApiErrorResponse> assistantReferenceUnavailable() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_REFERENCE_UNAVAILABLE",
                "助手绑定的模型或知识索引当前不可用");
    }

    @ExceptionHandler(AssistantVersionStateException.class)
    ResponseEntity<ApiErrorResponse> assistantVersionStateConflict() {
        return error(
                HttpStatus.CONFLICT,
                "ASSISTANT_VERSION_STATE_CONFLICT",
                "助手版本当前状态不允许该操作");
    }

    @ExceptionHandler(AssistantQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> assistantQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "ASSISTANT_QUOTA_EXCEEDED",
                "助手数量已经达到租户配额");
    }

    @ExceptionHandler(AssistantVersionQuotaExceededException.class)
    ResponseEntity<ApiErrorResponse> assistantVersionQuotaExceeded() {
        return error(
                HttpStatus.TOO_MANY_REQUESTS,
                "ASSISTANT_VERSION_QUOTA_EXCEEDED",
                "助手版本数量已经达到配额");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式不正确");
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(
                        code,
                        message,
                        TraceIds.next(identifiers),
                        Map.of()));
    }
}
