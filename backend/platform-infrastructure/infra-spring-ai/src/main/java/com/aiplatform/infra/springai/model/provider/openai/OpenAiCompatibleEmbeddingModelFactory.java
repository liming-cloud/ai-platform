package com.aiplatform.infra.springai.model.provider.openai;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.openai.client.OpenAIClient;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible EmbeddingModel 工厂，按运行时请求生成 provider 级模型实例。
 */
@Component
public class OpenAiCompatibleEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    private final SecureModelHttpTransport transport;

    public OpenAiCompatibleEmbeddingModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return OpenAiCompatibleSpringAiApiFactory.supports(request);
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        OpenAiCompatibleSpringAiApiFactory.validate(request);
        OpenAIClient client = transport.openAiClient(request.endpoint(), request.credentialMaterial());
        OpenAiEmbeddingOptions embeddingOptions = toOpenAiEmbeddingOptions(options, request);
        return OpenAiEmbeddingModel.builder()
                .openAiClient(client)
                .options(embeddingOptions)
                .build();
    }

    private static OpenAiEmbeddingOptions toOpenAiEmbeddingOptions(
            EmbeddingOptions options,
            SpringAiModelRuntimeRequest request
    ) {
        OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder()
                .baseUrl(OpenAiCompatibleSpringAiApiFactory.baseUrl(request.endpoint()))
                .apiKey(request.credentialMaterial())
                .model(request.modelCode())
                .maxRetries(0);
        if (options.getDimensions() != null) {
            builder.dimensions(options.getDimensions());
        }
        return builder.build();
    }
}
