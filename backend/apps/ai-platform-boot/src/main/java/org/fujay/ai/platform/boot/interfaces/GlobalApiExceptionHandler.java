package org.fujay.ai.platform.boot.interfaces;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.fujay.ai.platform.shared.error.CommonErrorCode;
import org.fujay.ai.platform.shared.error.ErrorCode;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.response.ApiResponse;
import org.fujay.ai.platform.shared.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin() {
        return failure(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotPermission() {
        return failure(CommonErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlatform(PlatformException exception) {
        ErrorCode errorCode = exception.errorCode();
        return failure(errorCode, statusOf(errorCode));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        LOGGER.warn("请求参数不正确：{}", exception.getMessage());
        return failure(CommonErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        LOGGER.error("服务内部错误", exception);
        return failure(CommonErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> failure(ErrorCode errorCode, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(errorCode.code(), errorCode.message(), TraceContext.currentTraceId()));
    }

    private HttpStatus statusOf(ErrorCode errorCode) {
        return switch (errorCode.code()) {
            case "COMMON-401" -> HttpStatus.UNAUTHORIZED;
            case "COMMON-403" -> HttpStatus.FORBIDDEN;
            case "COMMON-400" -> HttpStatus.BAD_REQUEST;
            case "MODEL-400-CAPABILITY" -> HttpStatus.BAD_REQUEST;
            case "MODEL-404-PROVIDER", "MODEL-404-MODEL" -> HttpStatus.NOT_FOUND;
            case "MODEL-502-PROVIDER" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
