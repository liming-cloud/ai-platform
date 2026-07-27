package com.aiplatform.foundation.provider.deterministic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicEmbeddingModelTest {

    private final DeterministicEmbeddingModel model = new DeterministicEmbeddingModel();

    @Test
    void 相同文本得到相同的有限单位向量() {
        var request = request(List.of("青柚气泡水位于一层冷藏区"));

        var first = model.embed(request).vectors().getFirst();
        var second = model.embed(request).vectors().getFirst();

        assertEquals(first, second);
        assertEquals(DeterministicEmbeddingModel.DIMENSION, first.dimension());
        var norm = 0.0d;
        for (var value : first.values()) {
            assertTrue(Float.isFinite(value));
            norm += value * value;
        }
        assertEquals(1.0d, Math.sqrt(norm), 0.00001d);
    }

    @Test
    void 字符片段重合的文本余弦相似度高于无关文本() {
        var vectors = model.embed(request(List.of(
                "青柚气泡水在哪里",
                "青柚气泡水位于一层冷藏区",
                "图书馆周末开放时间"))).vectors();

        var matching = cosine(vectors.get(0).values(), vectors.get(1).values());
        var unrelated = cosine(vectors.get(0).values(), vectors.get(2).values());

        assertTrue(matching > unrelated);
    }

    @Test
    void 部署修订和期望维度不匹配时失败关闭() {
        assertThrows(
                IllegalArgumentException.class,
                () -> model.embed(new EmbeddingRequest(
                        new EmbeddingDeploymentRef(
                                320000000000000999L,
                                DeterministicEmbeddingModel.DEPLOYMENT_REVISION,
                                DeterministicEmbeddingModel.DIMENSION),
                        List.of("文本"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> model.embed(new EmbeddingRequest(
                        new EmbeddingDeploymentRef(
                                DeterministicEmbeddingModel.DEPLOYMENT_ID,
                                DeterministicEmbeddingModel.DEPLOYMENT_REVISION,
                                12),
                        List.of("文本"))));
    }

    private static EmbeddingRequest request(List<String> texts) {
        return new EmbeddingRequest(
                new EmbeddingDeploymentRef(
                        DeterministicEmbeddingModel.DEPLOYMENT_ID,
                        DeterministicEmbeddingModel.DEPLOYMENT_REVISION,
                        DeterministicEmbeddingModel.DIMENSION),
                texts);
    }

    private static double cosine(float[] left, float[] right) {
        var product = 0.0d;
        var leftNorm = 0.0d;
        var rightNorm = 0.0d;
        for (var index = 0; index < left.length; index++) {
            product += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        return product / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
