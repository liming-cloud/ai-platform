package com.aiplatform.infra.springai.model.provider.azureopenai;

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
 * Azure OpenAI EmbeddingModel 工厂，按 deployment 生成 provider 级模型实例。
 */
@Component
public class AzureOpenAiEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    private final SecureModelHttpTransport transport;

    public AzureOpenAiEmbeddingModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return AzureOpenAiSpringAiClientFactory.supports(request);
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        if (!(options instanceof OpenAiEmbeddingOptions azureOptions)) {
            throw new IllegalArgumentException("Azure OpenAI EmbeddingModel 需要 OpenAiEmbeddingOptions");
        }
        AzureOpenAiSpringAiClientFactory.validate(request);
        OpenAIClient client = transport.azureOpenAiClient(
                request.endpoint(),
                request.credentialMaterial(),
                azureOptions.getMicrosoftFoundryServiceVersion()
        );
        return OpenAiEmbeddingModel.builder()
                .openAiClient(client)
                .options(azureOptions)
                .build();
    }
}
