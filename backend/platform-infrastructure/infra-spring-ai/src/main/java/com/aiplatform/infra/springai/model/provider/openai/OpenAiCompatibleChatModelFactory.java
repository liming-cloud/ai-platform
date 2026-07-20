package com.aiplatform.infra.springai.model.provider.openai;

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
 * OpenAI-compatible ChatModel 工厂，禁止业务上下文直接装配具体 provider 类。
 */
@Component
public class OpenAiCompatibleChatModelFactory implements SpringAiChatModelFactory {
    private final SecureModelHttpTransport transport;

    public OpenAiCompatibleChatModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return OpenAiCompatibleSpringAiApiFactory.supports(request);
    }

    @Override
    public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
        OpenAiCompatibleSpringAiApiFactory.validate(request);
        OpenAIClient client = transport.openAiClient(request.endpoint(), request.credentialMaterial());
        OpenAiChatOptions openAiOptions = toOpenAiChatOptions(options, request);
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async())
                .options(openAiOptions)
                .build();
    }

    private static OpenAiChatOptions toOpenAiChatOptions(ChatOptions options, SpringAiModelRuntimeRequest request) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .baseUrl(OpenAiCompatibleSpringAiApiFactory.baseUrl(request.endpoint()))
                .apiKey(request.credentialMaterial())
                .model(request.modelCode())
                .maxRetries(0);
        if (options.getMaxTokens() != null) {
            builder.maxTokens(options.getMaxTokens());
        }
        if (options.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(options.getFrequencyPenalty());
        }
        if (options.getPresencePenalty() != null) {
            builder.presencePenalty(options.getPresencePenalty());
        }
        if (options.getStopSequences() != null && !options.getStopSequences().isEmpty()) {
            builder.stop(options.getStopSequences());
        }
        if (options.getTemperature() != null) {
            builder.temperature(options.getTemperature());
        }
        if (options.getTopP() != null) {
            builder.topP(options.getTopP());
        }
        return builder.build();
    }
}
