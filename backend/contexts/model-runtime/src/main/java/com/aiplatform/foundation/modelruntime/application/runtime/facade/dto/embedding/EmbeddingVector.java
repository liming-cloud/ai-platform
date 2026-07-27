package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding;

import java.util.Arrays;
import java.util.Objects;

/**
 * 带原始输入顺序的有限浮点向量。
 */
public final class EmbeddingVector {

    private final int index;
    private final float[] values;

    public EmbeddingVector(int index, float[] values) {
        if (index < 0) {
            throw new IllegalArgumentException("嵌入结果顺序不能为负数");
        }
        Objects.requireNonNull(values, "嵌入向量不能为空");
        if (values.length < 1 || values.length > 2000) {
            throw new IllegalArgumentException("嵌入向量维度超出平台范围");
        }
        for (var value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("嵌入向量只能包含有限数值");
            }
        }
        this.index = index;
        this.values = values.clone();
    }

    public int index() {
        return index;
    }

    public int dimension() {
        return values.length;
    }

    public float[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof EmbeddingVector other
                && index == other.index
                && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(index) + Arrays.hashCode(values);
    }
}
