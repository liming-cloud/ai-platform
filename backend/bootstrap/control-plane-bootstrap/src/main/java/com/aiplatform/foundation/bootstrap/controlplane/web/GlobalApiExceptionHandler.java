package com.aiplatform.foundation.bootstrap.controlplane.web;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
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

    @ExceptionHandler(OperationsPermissionDeniedException.class)
    ResponseEntity<ApiErrorResponse> operationsAccessDenied() {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限");
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
