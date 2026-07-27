package com.aiplatform.foundation.shared.kernel.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台内可安全归一化的异常基类。
 *
 * <p>对外消息始终来自错误码；详情只保留错误码白名单字段，原因异常不对外暴露。</p>
 */
public abstract class FoundationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9_]*\\."
                    + "(REQUEST|AUTHENTICATION|AUTHORIZATION|RESOURCE|CONFLICT|QUOTA|"
                    + "DEPENDENCY_UNAVAILABLE|DEPENDENCY_RESPONSE|SYSTEM)\\."
                    + "\\d{4}$");

    private final transient ErrorCode errorCode;
    private final transient Map<String, Object> safeDetails;

    protected FoundationException(ErrorCode errorCode) {
        this(errorCode, Map.of(), null);
    }

    protected FoundationException(
            ErrorCode errorCode,
            Map<String, ?> details) {
        this(errorCode, details, null);
    }

    protected FoundationException(
            ErrorCode errorCode,
            Map<String, ?> details,
            Throwable cause) {
        super(validate(errorCode).defaultMessage(), cause);
        this.errorCode = errorCode;
        this.safeDetails = safeDetails(errorCode, details);
    }

    public final ErrorCode errorCode() {
        return errorCode;
    }

    public final Map<String, Object> safeDetails() {
        return safeDetails;
    }

    public final boolean retryable() {
        return errorCode.retryable();
    }

    private static ErrorCode validate(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "错误码不能为空");
        Objects.requireNonNull(errorCode.type(), "错误类别不能为空");
        Objects.requireNonNull(errorCode.allowedDetailFields(), "详情白名单不能为空");
        if (!CODE_PATTERN.matcher(errorCode.code()).matches()) {
            throw new IllegalArgumentException("错误编码格式不正确");
        }
        var category = errorCode.code().split("\\.", -1)[1];
        if (!category.equals(errorCode.type().name())) {
            throw new IllegalArgumentException("错误编码类别与错误类型不一致");
        }
        if (errorCode.defaultMessage() == null
                || errorCode.defaultMessage().isBlank()) {
            throw new IllegalArgumentException("错误默认消息不能为空");
        }
        return errorCode;
    }

    private static Map<String, Object> safeDetails(
            ErrorCode errorCode,
            Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        for (var field : errorCode.allowedDetailFields()) {
            if (details.containsKey(field) && details.get(field) != null) {
                result.put(field, details.get(field));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
