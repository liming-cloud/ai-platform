package com.aiplatform.shared.web.exception;

import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.AuthorizationRequestTarget;
import com.aiplatform.shared.kernel.RequestableAuthorizationException;
import com.aiplatform.shared.web.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_ERROR_CODE = "AIP-COMMON-VALIDATION-2001";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        return ApiResponse.failure(VALIDATION_ERROR_CODE, "参数校验失败");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException exception) {
        return ApiResponse.failure(VALIDATION_ERROR_CODE, "参数校验失败");
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> handleApplication(ApplicationException exception) {
        HttpStatus status = statusFor(exception.errorCode().code());
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(exception.errorCode().code(), exception.errorCode().message()));
    }

    @ExceptionHandler(RequestableAuthorizationException.class)
    public ResponseEntity<ApiResponse<AuthorizationRequestTarget>> handleRequestableAuthorization(
            RequestableAuthorizationException exception
    ) {
        HttpStatus status = statusFor(exception.errorCode().code());
        return ResponseEntity.status(status).body(ApiResponse.failure(
                exception.errorCode().code(),
                exception.errorCode().message(),
                exception.target()
        ));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        LOGGER.error("请求处理发生未预期异常", exception);
        return ApiResponse.failure("AIP-COMMON-8000", "系统异常");
    }

    private static HttpStatus statusFor(String code) {
        if (code.endsWith("LOGINRATELIMITED")) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (code.endsWith("AUTHPROTECTIONUNAVAILABLE")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code.endsWith("AUTHFAILED") || code.endsWith("SESSIONINVALID")) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code.endsWith("USERDISABLED") || code.endsWith("PERMISSIONDENIED")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.contains("AUTHZ-5003") || code.contains("FEATURE-5005")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.endsWith("NOTFOUND") || code.contains("RESOURCE-5004")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.endsWith("CONFLICT")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
