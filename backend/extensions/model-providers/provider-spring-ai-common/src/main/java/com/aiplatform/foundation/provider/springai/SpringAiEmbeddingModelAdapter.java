package com.aiplatform.foundation.provider.springai;

import com.aiplatform.foundation.modelruntime.api.invocation.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.api.model.embedding.EmbeddingVector;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 将 Spring AI 嵌入模型限制在平台框架无关契约之后。
 */
public final class SpringAiEmbeddingModelAdapter implements EmbeddingModelPort {

    private final EmbeddingModel model;
    private final EmbeddingDeploymentRef deployment;

    public SpringAiEmbeddingModelAdapter(
            EmbeddingModel model,
            EmbeddingDeploymentRef deployment) {
        this.model = Objects.requireNonNull(model, "Spring AI 嵌入模型不能为空");
        this.deployment = Objects.requireNonNull(deployment, "嵌入部署不能为空");
    }

    @Override
    public EmbeddingBatch embed(EmbeddingRequest request) {
        Objects.requireNonNull(request, "嵌入请求不能为空");
        if (!deployment.equals(request.deployment())) {
            throw new IllegalArgumentException("嵌入请求绑定的模型部署不匹配");
        }
        var values = model.embed(request.texts());
        if (values.size() != request.texts().size()) {
            throw new IllegalStateException("嵌入结果数量与请求不一致");
        }
        var vectors = new ArrayList<EmbeddingVector>(values.size());
        for (var index = 0; index < values.size(); index++) {
            vectors.add(new EmbeddingVector(index, values.get(index)));
        }
        return new EmbeddingBatch(deployment, vectors);
    }
}
