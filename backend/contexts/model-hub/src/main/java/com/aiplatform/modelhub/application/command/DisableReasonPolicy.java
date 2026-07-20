package com.aiplatform.modelhub.application.command;

final class DisableReasonPolicy {
    static final int MAX_LENGTH = 500;

    private DisableReasonPolicy() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("停用原因不能为空");
        }
        String reason = value.strip();
        if (reason.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("停用原因长度不能超过 " + MAX_LENGTH + " 个字符");
        }
        if (reason.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("停用原因不能包含控制字符");
        }
        return reason;
    }
}
