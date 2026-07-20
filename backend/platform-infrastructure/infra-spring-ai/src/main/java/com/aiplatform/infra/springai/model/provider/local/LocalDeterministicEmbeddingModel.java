package com.aiplatform.infra.springai.model.provider.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 本地确定性向量模型，用于本地环境和验收环境以真实 EmbeddingModel 抽象完成向量化调用。
 */
final class LocalDeterministicEmbeddingModel implements EmbeddingModel {
    private static final int DEFAULT_DIMENSION = 384;
    private final int dimension;

    LocalDeterministicEmbeddingModel(Integer dimension) {
        int resolvedDimension = dimension == null ? DEFAULT_DIMENSION : dimension;
        this.dimension = LocalDeterministicRuntimeContract.requireSupportedEmbeddingDimension(
                resolvedDimension,
                "维度"
        );
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int index = 0; index < instructions.size(); index++) {
            embeddings.add(new Embedding(embedText(instructions.get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    private float[] embedText(String text) {
        String normalizedText = text == null ? "" : text;
        float[] vector = new float[dimension];
        int offset = 0;
        int round = 0;
        while (offset < dimension) {
            byte[] digest = digest(normalizedText, round++);
            for (byte value : digest) {
                if (offset >= dimension) {
                    break;
                }
                vector[offset++] = ((value & 0xFF) - 127.5f) / 127.5f;
            }
        }
        return normalize(vector);
    }

    private static byte[] digest(String text, int round) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(Integer.toString(round).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static float[] normalize(float[] vector) {
        double sumOfSquares = 0;
        for (float value : vector) {
            sumOfSquares += value * value;
        }
        if (sumOfSquares == 0) {
            return vector;
        }
        float norm = (float) Math.sqrt(sumOfSquares);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
        return vector;
    }
}
