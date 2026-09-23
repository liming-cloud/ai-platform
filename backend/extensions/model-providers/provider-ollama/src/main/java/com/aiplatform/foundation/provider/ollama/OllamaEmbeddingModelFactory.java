package com.aiplatform.foundation.provider.ollama;

import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.provider.springai.SpringAiEmbeddingModelAdapter;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import java.util.Objects;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

/**
 * 使用 Spring AI 构造 Ollama 嵌入运行时。
 */
public final class OllamaEmbeddingModelFactory implements EmbeddingModelFactory {

    private final OutboundEndpointPolicy endpointPolicy;

    public OllamaEmbeddingModelFactory(OutboundEndpointPolicy endpointPolicy) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OLLAMA;
    }

    @Override
    public EmbeddingModelPort create(ModelRuntimeSpecification specification) {
        Objects.requireNonNull(specification, "模型运行时配置不能为空");
        if (specification.providerKind() != providerKind()
                || specification.modelType() != ModelType.EMBEDDING) {
            throw new IllegalArgumentException("Ollama 嵌入模型配置类型不匹配");
        }
        endpointPolicy.verify(specification.endpoint());
        var api = OllamaApi.builder()
                .baseUrl(specification.endpoint().toString())
                .build();
        var options = OllamaEmbeddingOptions.builder()
                .model(specification.modelCode())
                .dimensions(specification.embeddingDimension())
                .build();
        var model = OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .options(options)
                .build();
        return new SpringAiEmbeddingModelAdapter(
                model,
                new EmbeddingDeploymentRef(
                        specification.deploymentId(),
                        specification.deploymentRevision(),
                        specification.embeddingDimension()));
    }
}
