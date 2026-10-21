package com.aiplatform.foundation.modelruntime.application.extension.facade.verification;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import java.util.List;
import java.util.Objects;

/** 仅内存中的样本观察；价格、授权及最终通过由模型上下文独立判定。 */
public record PlatformCapabilitySampleResult(String binding, ModelType type, String text, List<Embedding> embeddings,
        List<Ranked> ranks, Long inputTokens, Long outputTokens, boolean unexpectedContent) {
    public PlatformCapabilitySampleResult {
        Objects.requireNonNull(binding, "样本绑定不能为空");
        Objects.requireNonNull(type, "样本能力不能为空");
        embeddings = List.copyOf(embeddings);
        ranks = List.copyOf(ranks);
    }
    /** 单输入向量及供应商的原始索引，维度和数值由安全样本校验。 */
    public record Embedding(int index, List<Double> values) {
        public Embedding { values = List.copyOf(values); }
    }
    /** 原始候选索引与有限性待校验的得分，不假定得分位于 0～1。 */
    public record Ranked(int index, double score) {}
    @Override
    public String toString() { return "PlatformCapabilitySampleResult[type=" + type + ", 内容已隐藏]"; }
}
