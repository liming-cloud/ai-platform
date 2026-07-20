package com.aiplatform.appstudio.domain.port.modelpolicy.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.ValueObject;
import java.util.Map;

/**
 * AI 应用模型使用策略决策，允许时携带可被 Spring AI 基础设施消费的运行时描述。
 */
public record AppModelUsageDecision(
        boolean allowed,
        String decisionCode,
        String reason,
        AppModelRuntimeDescriptor runtimeDescriptor,
        Map<String, String> auditAttributes
) implements ValueObject {
    public AppModelUsageDecision {
        decisionCode = AppStudioPortContracts.requireText(decisionCode, "决策编码");
        auditAttributes = AppStudioPortContracts.normalizeTextMap(auditAttributes, "审计属性");
        if (allowed) {
            reason = AppStudioPortContracts.requireValue(reason, "决策原因").trim();
            runtimeDescriptor = AppStudioPortContracts.requireValue(runtimeDescriptor, "模型运行时描述");
        } else {
            reason = AppStudioPortContracts.requireText(reason, "拒绝原因");
            if (runtimeDescriptor != null) {
                throw new IllegalArgumentException("拒绝决策不能携带模型运行时描述");
            }
        }
    }
}
