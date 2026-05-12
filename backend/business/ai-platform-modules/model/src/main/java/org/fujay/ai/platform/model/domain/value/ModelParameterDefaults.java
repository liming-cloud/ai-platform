package org.fujay.ai.platform.model.domain.value;

import java.math.BigDecimal;
import org.fujay.ai.platform.shared.ddd.ValueObject;

public record ModelParameterDefaults(
        Integer contextWindow,
        Integer maxOutputTokens,
        Integer embeddingDimensions,
        BigDecimal temperature,
        BigDecimal topP,
        Integer topK,
        BigDecimal frequencyPenalty,
        BigDecimal presencePenalty,
        String reasoningEffort) implements ValueObject {

    public void validate(ProviderType providerType, ModelCapabilityType capabilityType) {
        requirePositive(contextWindow, "上下文窗口必须大于 0");
        requirePositive(maxOutputTokens, "最大输出 token 必须大于 0");
        requirePositive(embeddingDimensions, "向量维度必须大于 0");
        requireBetweenZeroAndTwo(temperature, "temperature 必须在 0 到 2 之间");
        requireBetweenZeroAndOne(topP, "topP 必须在 0 到 1 之间");
        requireBetweenMinusTwoAndTwo(frequencyPenalty, "frequencyPenalty 必须在 -2 到 2 之间");
        requireBetweenMinusTwoAndTwo(presencePenalty, "presencePenalty 必须在 -2 到 2 之间");
        if (providerType == ProviderType.OPENAI && topK != null) {
            throw new IllegalArgumentException("OpenAI 模型当前不支持 topK 参数");
        }
        if (capabilityType == ModelCapabilityType.EMBEDDING && embeddingDimensions == null) {
            throw new IllegalArgumentException("Embedding 模型必须配置向量维度");
        }
    }

    private void requirePositive(Integer value, String message) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireBetweenZeroAndTwo(BigDecimal value, String message) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(2)) > 0)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireBetweenZeroAndOne(BigDecimal value, String message) {
        if (value != null && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireBetweenMinusTwoAndTwo(BigDecimal value, String message) {
        if (value != null && (value.compareTo(BigDecimal.valueOf(-2)) < 0
                || value.compareTo(BigDecimal.valueOf(2)) > 0)) {
            throw new IllegalArgumentException(message);
        }
    }
}
