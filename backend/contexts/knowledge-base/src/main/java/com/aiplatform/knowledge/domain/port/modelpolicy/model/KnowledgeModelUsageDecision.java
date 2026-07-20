package com.aiplatform.knowledge.domain.port.modelpolicy.model;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库模型使用策略决策，拒绝时不得继续进入 Spring AI 运行时调用。
 */
public record KnowledgeModelUsageDecision(
        boolean allowed,
        String decisionCode,
        String reason,
        KnowledgeModelRuntimeDescriptor runtimeDescriptor,
        Map<String, String> auditAttributes
) implements ValueObject {
    public KnowledgeModelUsageDecision {
        decisionCode = requireText(decisionCode, "决策编码");
        auditAttributes = normalizeTextMap(auditAttributes, "审计属性");
        if (allowed) {
            reason = Objects.requireNonNull(reason, "决策原因不能为空").trim();
            requireValue(runtimeDescriptor, "模型运行时描述");
        } else {
            reason = requireText(reason, "拒绝原因");
            if (runtimeDescriptor != null) {
                throw new IllegalArgumentException("拒绝决策不能携带模型运行时描述");
            }
        }
    }

    public static KnowledgeModelUsageDecision deny(String decisionCode, String reason) {
        return new KnowledgeModelUsageDecision(false, decisionCode, reason, null, Map.of());
    }

    public static KnowledgeModelUsageDecision allow(
            KnowledgeModelRuntimeDescriptor runtimeDescriptor,
            Map<String, String> auditAttributes
    ) {
        requireValue(runtimeDescriptor, "模型运行时描述");
        return new KnowledgeModelUsageDecision(true, "allowed", "", runtimeDescriptor, auditAttributes);
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static Map<String, String> normalizeTextMap(Map<String, String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = requireText(entry.getKey(), fieldName + " key");
            String value = requireText(entry.getValue(), fieldName + " value");
            if (normalized.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(fieldName + " key重复：" + key);
            }
        }
        return Map.copyOf(normalized);
    }
}
