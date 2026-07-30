package com.aiplatform.foundation.platform.web.error;

import com.aiplatform.foundation.shared.contract.error.ApiErrorResponse;
import com.aiplatform.foundation.shared.kernel.error.FoundationException;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 将平台异常映射为统一错误响应，并阻止内部实现信息泄漏。
 */
@RestControllerAdvice
public class FoundationExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(FoundationExceptionHandler.class);

    private final EntityIdGenerator identifiers;

    public FoundationExceptionHandler(EntityIdGenerator identifiers) {
        this.identifiers = Objects.requireNonNull(
                identifiers,
                "追踪标识生成器不能为空");
    }

    @ExceptionHandler(FoundationException.class)
    public ResponseEntity<ApiErrorResponse> foundationException(
            FoundationException exception) {
        var code = exception.errorCode();
        return ResponseEntity.status(HttpErrorTypeMapping.status(code.type()))
                .body(response(
                        code.code(),
                        code.defaultMessage(),
                        exception.safeDetails(),
                        TraceIds.next(identifiers)));
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiErrorResponse> invalidRequest(Exception exception) {
        var code = CommonErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(HttpErrorTypeMapping.status(code.type()))
                .body(response(
                        code.code(),
                        code.defaultMessage(),
                        Map.of(),
                        TraceIds.next(identifiers)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unrecognized(Exception exception) {
        if (exception instanceof NoResourceFoundException) {
            var code = CommonErrorCode.RESOURCE_NOT_FOUND;
            return ResponseEntity.status(HttpErrorTypeMapping.status(code.type()))
                    .body(response(
                            code.code(),
                            code.defaultMessage(),
                            Map.of(),
                            TraceIds.next(identifiers)));
        }
        var traceId = TraceIds.next(identifiers);
        LOGGER.error("未识别异常，追踪标识：{}", traceId, exception);
        var code = CommonErrorCode.SYSTEM_FAILURE;
        return ResponseEntity.status(HttpErrorTypeMapping.status(code.type()))
                .body(response(
                        code.code(),
                        code.defaultMessage(),
                        Map.of(),
                        traceId));
    }

    private static ApiErrorResponse response(
            String code,
            String message,
            Map<String, Object> details,
            String traceId) {
        return new ApiErrorResponse(code, message, traceId, details);
    }
}
