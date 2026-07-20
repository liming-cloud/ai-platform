package com.aiplatform.shared.kernel;

import java.util.regex.Pattern;

/**
 * 平台统一错误码，以 AIP 开头并包含至少模块和编码两个受控段，用于 API、日志和审计保持一致。
 */
public record ErrorCode(String code, String message) {
    private static final Pattern CODE_PATTERN = Pattern.compile("^AIP(?:-[A-Z0-9]+){2,}$");

    public ErrorCode {
        code = requireText(code, "错误码");
        message = requireText(message, "错误消息");
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("错误码必须匹配 AIP-<MODULE>-<CODE>");
        }
    }

    public static ErrorCode of(String code, String message) {
        return new ErrorCode(code, message);
    }

    @Override
    public String toString() {
        return code + " " + message;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
