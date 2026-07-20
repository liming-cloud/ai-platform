package com.aiplatform.infra.springai.model.provider.vertexai;

import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingModel;
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Vertex AI Text EmbeddingModel 工厂，按 project/location/publisher 装配连接详情。
 */
@Component
public class VertexAiEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return VertexAiSpringAiClientFactory.supports(request);
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        if (!(options instanceof VertexAiTextEmbeddingOptions vertexOptions)) {
            throw new IllegalArgumentException("Vertex AI EmbeddingModel 需要 VertexAiTextEmbeddingOptions");
        }
        return new VertexAiTextEmbeddingModel(
                connectionDetails(request),
                vertexOptions,
                singleAttemptRetryTemplate(),
                ObservationRegistry.NOOP
        );
    }

    private static VertexAiEmbeddingConnectionDetails connectionDetails(SpringAiModelRuntimeRequest request) {
        VertexAiEmbeddingConnectionDetails.Builder builder = VertexAiEmbeddingConnectionDetails.builder()
                .projectId(VertexAiProviderSupport.projectId(request))
                .location(VertexAiProviderSupport.location(request))
                .publisher(VertexAiProviderSupport.publisher(request))
                .predictionServiceSettings(VertexAiSpringAiClientFactory.predictionServiceSettings(request));
        VertexAiProviderSupport.apiEndpoint(request).ifPresent(builder::apiEndpoint);
        return builder.build();
    }

    private static RetryTemplate singleAttemptRetryTemplate() {
        return new RetryTemplate(RetryPolicy.withMaxRetries(0));
    }
}
