package com.aiplatform.shared.kernel;

/**
 * 领域异常表示业务不变量被破坏，必须携带统一错误码以便审计和前端映射。
 */
public class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String detail;

    public DomainException(ErrorCode errorCode, String detail) {
        super(formatMessage(errorCode, detail));
        this.errorCode = requireErrorCode(errorCode);
        this.detail = requireText(detail, "领域异常详情");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    private static String formatMessage(ErrorCode errorCode, String detail) {
        return requireErrorCode(errorCode) + ": " + requireText(detail, "领域异常详情");
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("错误码不能为空");
        }
        return errorCode;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
