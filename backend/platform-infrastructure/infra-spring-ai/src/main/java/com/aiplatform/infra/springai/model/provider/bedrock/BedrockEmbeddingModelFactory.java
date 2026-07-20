package com.aiplatform.infra.springai.model.provider.bedrock;

import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingOptions;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.stereotype.Component;

/**
 * Amazon Bedrock EmbeddingModel 工厂，按模型供应商族装配 Titan 或 Cohere embedding。
 */
@Component
public class BedrockEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return BedrockSpringAiClientFactory.supports(request);
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        if (BedrockProviderSupport.isCohereEmbedding(request)) {
            if (!(options instanceof BedrockCohereEmbeddingOptions cohereOptions)) {
                throw new IllegalArgumentException("Amazon Bedrock Cohere EmbeddingModel 需要 BedrockCohereEmbeddingOptions");
            }
            return new BedrockCohereEmbeddingModel(
                    BedrockSpringAiClientFactory.cohereEmbeddingApi(request),
                    cohereOptions
            );
        }
        if (!(options instanceof BedrockTitanEmbeddingOptions titanOptions)) {
            throw new IllegalArgumentException("Amazon Bedrock Titan EmbeddingModel 需要 BedrockTitanEmbeddingOptions");
        }
        BedrockTitanEmbeddingModel model = new BedrockTitanEmbeddingModel(
                BedrockSpringAiClientFactory.titanEmbeddingApi(request),
                ObservationRegistry.NOOP
        );
        if (titanOptions.getInputType() != null) {
            return model.withInputType(titanOptions.getInputType());
        }
        return model;
    }
}
