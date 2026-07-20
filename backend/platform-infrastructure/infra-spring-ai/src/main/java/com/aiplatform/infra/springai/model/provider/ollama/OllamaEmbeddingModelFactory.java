package com.aiplatform.infra.springai.model.provider.ollama;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.stereotype.Component;

/**
 * Ollama EmbeddingModel 工厂，按 provider 运行时请求生成模型实例。
 */
@Component
public class OllamaEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    private final SecureModelHttpTransport transport;

    public OllamaEmbeddingModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return OllamaSpringAiApiFactory.supports(request);
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        if (!(options instanceof OllamaEmbeddingOptions ollamaOptions)) {
            throw new IllegalArgumentException("Ollama EmbeddingModel 需要 OllamaEmbeddingOptions");
        }
        return OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaSpringAiApiFactory.create(request, transport))
                .options(ollamaOptions)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .build();
    }
}
