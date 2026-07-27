package com.aiplatform.foundation.provider.deterministic;

import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.api.model.EmbeddingVector;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * 用于开发、合同测试和演示自检的确定性字符片段嵌入模型。
 *
 * <p>该实现不代表真实语义模型质量，不得用于生产质量声明。</p>
 */
public final class DeterministicEmbeddingModel implements EmbeddingModelPort {

    public static final long DEPLOYMENT_ID = 320000000000000128L;
    public static final long DEPLOYMENT_REVISION = 1;
    public static final int DIMENSION = 128;

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final EmbeddingDeploymentRef deployment;

    public DeterministicEmbeddingModel() {
        this(new EmbeddingDeploymentRef(DEPLOYMENT_ID, DEPLOYMENT_REVISION, DIMENSION));
    }

    public DeterministicEmbeddingModel(EmbeddingDeploymentRef deployment) {
        this.deployment = java.util.Objects.requireNonNull(
                deployment,
                "确定性嵌入部署不能为空");
    }

    @Override
    public EmbeddingBatch embed(EmbeddingRequest request) {
        requireDeployment(request);
        var vectors = new ArrayList<EmbeddingVector>(request.texts().size());
        for (var index = 0; index < request.texts().size(); index++) {
            vectors.add(new EmbeddingVector(
                    index,
                    embedText(request.texts().get(index))));
        }
        return new EmbeddingBatch(request.deployment(), vectors);
    }

    private float[] embedText(String text) {
        var normalized = Normalizer
                .normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        var codePoints = normalized.codePoints()
                .filter(value -> !Character.isWhitespace(value))
                .toArray();
        var vector = new float[deployment.dimension()];
        for (var index = 0; index < codePoints.length; index++) {
            accumulate(vector, hash(codePoints[index]), 1.0f);
            if (index + 1 < codePoints.length) {
                var pair = ((long) codePoints[index] << 32)
                        ^ Integer.toUnsignedLong(codePoints[index + 1]);
                accumulate(vector, hash(pair), 2.0f);
            }
        }
        normalize(vector);
        return vector;
    }

    private static void accumulate(float[] vector, long hash, float weight) {
        var index = Math.floorMod((int) hash, vector.length);
        var sign = (hash & Long.MIN_VALUE) == 0 ? 1.0f : -1.0f;
        vector[index] += sign * weight;
    }

    private static long hash(long value) {
        var result = FNV_OFFSET;
        for (var shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            result ^= (value >>> shift) & 0xffL;
            result *= FNV_PRIME;
        }
        result ^= result >>> 32;
        result *= 0xd6e8feb86659fd93L;
        result ^= result >>> 32;
        return result;
    }

    private static void normalize(float[] vector) {
        var squaredNorm = 0.0d;
        for (var value : vector) {
            squaredNorm += value * value;
        }
        if (squaredNorm == 0.0d) {
            throw new IllegalArgumentException("嵌入文本没有可编码内容");
        }
        var divisor = Math.sqrt(squaredNorm);
        for (var index = 0; index < vector.length; index++) {
            vector[index] = (float) (vector[index] / divisor);
        }
    }

    private void requireDeployment(EmbeddingRequest request) {
        if (!deployment.equals(request.deployment())) {
            throw new IllegalArgumentException("确定性嵌入部署不匹配");
        }
    }
}
