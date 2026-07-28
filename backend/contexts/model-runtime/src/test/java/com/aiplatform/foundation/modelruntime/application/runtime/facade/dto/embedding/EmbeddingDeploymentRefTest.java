package com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmbeddingDeploymentRefTest {

    @Test
    void 应接受2048维嵌入部署引用() {
        var reference = new EmbeddingDeploymentRef(320000000000000001L, 1, 2048);

        assertEquals(2048, reference.dimension());
    }
}
