package com.aiplatform.infra.springai.model.factory;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Spring AI EmbeddingModel 工厂，只接收平台内部受控运行时请求。
 */
public interface SpringAiEmbeddingModelFactory {
    boolean supports(SpringAiModelRuntimeRequest request);

    EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options);
}
