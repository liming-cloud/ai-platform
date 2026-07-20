package com.aiplatform.modelhub.domain.modelpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUsagePolicyDecision;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyDecisionTest {
    @Test
    void rejectsDeniedDecisionWithoutReasonInsteadOfEmptyReasonFallback() {
        assertThatThrownBy(() -> ModelUsagePolicyDecision.deny(
                "model_policy_disabled",
                null,
                "policy-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝原因不能为空");
    }

    @Test
    void rejectsDecisionWithoutPolicyVersionInsteadOfEmptyVersionFallback() {
        assertThatThrownBy(() -> ModelUsagePolicyDecision.allow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("策略版本不能为空");
    }

    @Test
    void rejectsAllowedDecisionWithNullReasonInsteadOfEmptyReasonFallback() {
        assertThatThrownBy(() -> new ModelUsagePolicyDecision(
                true,
                "allowed",
                null,
                "policy-v1"
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("决策原因不能为空");
    }

    @Test
    void normalizesDecisionFactsBeforeAuditUse() {
        ModelUsagePolicyDecision decision = new ModelUsagePolicyDecision(
                false,
                " model_policy_disabled ",
                " 模型策略未启用 ",
                " policy-v1 "
        );

        assertThat(decision.decisionCode()).isEqualTo("model_policy_disabled");
        assertThat(decision.reason()).isEqualTo("模型策略未启用");
        assertThat(decision.policyVersion()).isEqualTo("policy-v1");
    }
}
