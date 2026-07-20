package com.aiplatform.appstudio.domain.port.modelpolicy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppModelUsageDecisionTest {
    @Test
    void rejectsDeniedDecisionWithoutReason() {
        assertThatThrownBy(() -> new AppModelUsageDecision(
                false,
                "denied",
                null,
                null,
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝原因不能为空");
    }

    @Test
    void rejectsAllowedDecisionWithoutRuntimeDescriptor() {
        assertThatThrownBy(() -> new AppModelUsageDecision(
                true,
                "allowed",
                "",
                null,
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时描述不能为空");
    }

    @Test
    void rejectsDeniedDecisionWithRuntimeDescriptor() {
        assertThatThrownBy(() -> new AppModelUsageDecision(
                false,
                "denied",
                "没有可用模型策略",
                runtimeDescriptor(),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝决策不能携带模型运行时描述");
    }

    @Test
    void normalizesDecisionFactsBeforeAuditUse() {
        AppModelUsageDecision decision = new AppModelUsageDecision(
                false,
                " denied ",
                " 没有可用模型策略 ",
                null,
                Map.of(" traceId ", " trace-1 ")
        );

        assertThat(decision.decisionCode()).isEqualTo("denied");
        assertThat(decision.reason()).isEqualTo("没有可用模型策略");
        assertThat(decision.auditAttributes()).containsEntry("traceId", "trace-1");
    }

    @Test
    void rejectsBlankAuditAttributeInsteadOfLeakingDamagedAuditFacts() {
        assertThatThrownBy(() -> new AppModelUsageDecision(
                false,
                "denied",
                "没有可用模型策略",
                null,
                Map.of("traceId", " ")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计属性 value不能为空");
    }

    private static AppModelRuntimeDescriptor runtimeDescriptor() {
        return new AppModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new AppModelRuntimeConnectionRef("https://api.openai.example/v1", "api_key", "secret-1"),
                "gpt-4.1-mini",
                7,
                "CHAT",
                Set.of("chat"),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                "policy-v1"
        );
    }
}
