package com.aiplatform.foundation.knowledgedata.domain.indexversion.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 确定性分块产物。
 */
public record TextChunk(
        String id,
        int ordinal,
        String text,
        String sha256) implements ValueObject {

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    public TextChunk {
        if (!DIGEST.matcher(Objects.requireNonNull(id, "分块标识不能为空")).matches()) {
            throw new IllegalArgumentException("分块标识格式不正确");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("分块顺序不能为负数");
        }
        if (Objects.requireNonNull(text, "分块文本不能为空").isBlank()) {
            throw new IllegalArgumentException("分块文本不能为空");
        }
        if (!DIGEST.matcher(Objects.requireNonNull(sha256, "分块摘要不能为空")).matches()) {
            throw new IllegalArgumentException("分块摘要格式不正确");
        }
    }
}
