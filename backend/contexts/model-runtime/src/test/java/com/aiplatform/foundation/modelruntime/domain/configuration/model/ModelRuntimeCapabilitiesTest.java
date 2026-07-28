package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import org.junit.jupiter.api.Test;

class ModelRuntimeCapabilitiesTest {

    @Test
    void 对话与嵌入目录能力必须冻结各自必要参数() {
        var chat = ModelRuntimeCapabilities.chat(32_768, 2_048);
        var embedding = ModelRuntimeCapabilities.embedding(8_192, 1_024);

        chat.requireFor(ModelType.CHAT);
        embedding.requireFor(ModelType.EMBEDDING);
        assertEquals(2_048, chat.outputLimit());
        assertEquals(1_024, embedding.embeddingDimension());
    }

    @Test
    void 目录能力不能将对话输出上限混入嵌入模型() {
        assertThrows(IllegalArgumentException.class, () -> new ModelRuntimeCapabilities(
                8_192, 512, 1_024).requireFor(ModelType.EMBEDDING));
    }
}
