package com.aiplatform.shared.kernel;

/**
 * 应用异常表示用例编排、权限校验、外部依赖调用等应用层失败。
 */
public class ApplicationException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String detail;

    public ApplicationException(ErrorCode errorCode, String detail) {
        super(formatMessage(errorCode, detail));
        this.errorCode = requireErrorCode(errorCode);
        this.detail = requireText(detail, "应用异常详情");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    private static String formatMessage(ErrorCode errorCode, String detail) {
        return requireErrorCode(errorCode) + ": " + requireText(detail, "应用异常详情");
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
