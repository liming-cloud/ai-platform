package com.aiplatform.foundation.controlplane.web;

import com.aiplatform.foundation.organizationaccess.adapter.out.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.organizationaccess.application.authentication.InvalidCredentialsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把内部异常归一为不泄漏资源和凭据事实的公共错误。
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> invalidCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "认证失败");
    }

    @ExceptionHandler(InvalidAuthenticatedSessionException.class)
    ResponseEntity<ApiErrorResponse> invalidSession() {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "需要有效会话");
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiErrorResponse> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式不正确");
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(code, message, TraceIds.next(), Map.of()));
    }
}
