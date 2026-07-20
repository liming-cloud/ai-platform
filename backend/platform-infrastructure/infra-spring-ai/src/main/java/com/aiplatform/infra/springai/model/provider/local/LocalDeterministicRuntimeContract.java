package com.aiplatform.infra.springai.model.provider.local;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Objects;

/**
 * 本地确定性运行时的纵深连接契约。即使持久化数据被绕过或污染，运行时也不会接受
 * 外部地址或消费任何凭证材料。
 */
final class LocalDeterministicRuntimeContract {
    static final String ENDPOINT = "local://deterministic";
    static final int MAX_EMBEDDING_DIMENSION = 4096;

    private LocalDeterministicRuntimeContract() {
    }

    static void validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "本地确定性运行时请求不能为空");
        if (!ENDPOINT.equals(request.endpoint())) {
            throw new IllegalArgumentException("本地确定性运行时地址必须是 " + ENDPOINT);
        }
        if (!"no_auth".equalsIgnoreCase(request.authType())) {
            throw new IllegalArgumentException("本地确定性运行时仅支持 no_auth 鉴权");
        }
        if (request.credentialMaterial() != null && !request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("本地确定性运行时不允许携带凭证材料");
        }
    }

    static int requireConsistentEmbeddingDimensions(
            Integer embeddingDimension,
            Integer optionsDimensions
    ) {
        if (embeddingDimension == null) {
            throw new IllegalArgumentException("本地向量模型 embeddingDimension 不能为空");
        }
        int validatedMetadata = requireSupportedEmbeddingDimension(embeddingDimension, "embeddingDimension");
        if (optionsDimensions == null) {
            return validatedMetadata;
        }
        int validatedOptions = requireSupportedEmbeddingDimension(optionsDimensions, "dimensions");
        if (validatedOptions != validatedMetadata) {
            throw new IllegalArgumentException("本地向量模型 dimensions 必须与 embeddingDimension 一致");
        }
        return validatedMetadata;
    }

    static int requireSupportedEmbeddingDimension(Integer dimension, String fieldName) {
        Objects.requireNonNull(fieldName, "本地向量模型维度字段名不能为空");
        if (dimension == null || dimension < 1) {
            throw new IllegalArgumentException("本地向量模型 " + fieldName + " 必须大于等于 1");
        }
        if (dimension > MAX_EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "本地向量模型 " + fieldName + " 不得大于 " + MAX_EMBEDDING_DIMENSION
            );
        }
        return dimension;
    }
}
