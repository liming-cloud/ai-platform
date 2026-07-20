package com.aiplatform.identity.application.command;

/**
 * 高风险 IAM 配置变更原因的统一应用层边界。
 */
final class IamChangeReason {
    private static final int MAX_LENGTH = 300;

    private IamChangeReason() {
    }

    static String normalize(String value, String fieldName) {
        String normalized = UpdateIamMenuCommand.requireText(value, fieldName);
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + MAX_LENGTH + " 个字符");
        }
        return normalized;
    }
}
