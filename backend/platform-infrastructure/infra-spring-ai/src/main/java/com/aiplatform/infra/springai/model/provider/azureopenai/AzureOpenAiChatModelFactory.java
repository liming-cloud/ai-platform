package com.aiplatform.infra.springai.model.provider.azureopenai;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.openai.client.OpenAIClient;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * Azure OpenAI ChatModel 工厂，避免把 Azure deployment 语义塞进 OpenAI-compatible 分支。
 */
@Component
public class AzureOpenAiChatModelFactory implements SpringAiChatModelFactory {
    private final SecureModelHttpTransport transport;

    public AzureOpenAiChatModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return AzureOpenAiSpringAiClientFactory.supports(request);
    }

    @Override
    public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
        if (!(options instanceof OpenAiChatOptions azureOptions)) {
            throw new IllegalArgumentException("Azure OpenAI ChatModel 需要 OpenAiChatOptions");
        }
        AzureOpenAiSpringAiClientFactory.validate(request);
        OpenAIClient client = transport.azureOpenAiClient(
                request.endpoint(),
                request.credentialMaterial(),
                azureOptions.getMicrosoftFoundryServiceVersion()
        );
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async())
                .options(azureOptions)
                .build();
    }
}
