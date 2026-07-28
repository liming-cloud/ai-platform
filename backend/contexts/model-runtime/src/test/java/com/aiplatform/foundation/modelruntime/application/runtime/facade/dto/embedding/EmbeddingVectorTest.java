package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmbeddingVectorTest {

    @Test
    void 应接受火山方舟2048维嵌入向量() {
        var vector = new EmbeddingVector(0, new float[2048]);

        assertEquals(2048, vector.dimension());
    }

    @Test
    void 应拒绝超过向量存储上限的嵌入向量() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmbeddingVector(0, new float[16001]));
    }
}
