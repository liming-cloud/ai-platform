package com.aiplatform.modelhub.domain.modelpolicy.model;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.Objects;

/**
 * 模型使用策略决策值对象，表达允许、拒绝原因和策略版本审计属性。
 */
public record ModelUsagePolicyDecision(
        boolean allowed,
        String decisionCode,
        String reason,
        String policyVersion
) implements ValueObject {
    public ModelUsagePolicyDecision {
        decisionCode = requireText(decisionCode, "决策编码");
        policyVersion = requireText(policyVersion, "策略版本");
        if (allowed) {
            reason = Objects.requireNonNull(reason, "决策原因不能为空").trim();
        } else {
            reason = requireText(reason, "拒绝原因");
        }
    }

    public static ModelUsagePolicyDecision allow(String policyVersion) {
        return new ModelUsagePolicyDecision(true, "allowed", "", policyVersion);
    }

    public static ModelUsagePolicyDecision deny(String decisionCode, String reason, String policyVersion) {
        return new ModelUsagePolicyDecision(false, decisionCode, reason, policyVersion);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
