package com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IndexVectorTest {

    @Test
    void 应接受2048维索引向量() {
        var vector = new IndexVector(
                220000000000000001L,
                1,
                "a".repeat(64),
                0,
                "M2 合成文本",
                "b".repeat(64),
                new float[2048]);

        assertEquals(2048, vector.dimension());
    }
}
