package com.aiplatform.foundation.modelruntime.api.model;

import java.util.List;
import java.util.Objects;

/**
 * 按输入顺序执行的受限嵌入请求。
 */
public record EmbeddingRequest(
        EmbeddingDeploymentRef deployment,
        List<String> texts) {

    public static final int MAX_BATCH_SIZE = 128;
    public static final int MAX_TEXT_CODE_POINTS = 8192;

    public EmbeddingRequest {
        Objects.requireNonNull(deployment, "嵌入部署不能为空");
        Objects.requireNonNull(texts, "嵌入文本不能为空");
        texts = List.copyOf(texts);
        if (texts.isEmpty() || texts.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("嵌入批次数量超出平台范围");
        }
        for (var text : texts) {
            Objects.requireNonNull(text, "嵌入文本不能包含空值");
            if (text.isBlank()
                    || text.codePointCount(0, text.length()) > MAX_TEXT_CODE_POINTS) {
                throw new IllegalArgumentException("嵌入文本格式不正确");
            }
        }
    }
}
