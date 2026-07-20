package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.ValueObject;

/**
 * 授权决策值对象，用于统一表达允许或拒绝以及可审计的拒绝原因。
 */
public record AuthorizationDecision(boolean allowed, String decisionCode, String reason) implements ValueObject {
    public AuthorizationDecision {
        decisionCode = requireText(decisionCode, "决策编码");
        if (allowed) {
            reason = requireValue(reason, "决策原因").trim();
        } else {
            reason = requireText(reason, "拒绝原因");
        }
    }

    public static AuthorizationDecision allow(String decisionCode) {
        return new AuthorizationDecision(true, decisionCode, "");
    }

    public static AuthorizationDecision deny(String decisionCode, String reason) {
        return new AuthorizationDecision(false, decisionCode, reason);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
