package org.fujay.ai.platform.ai;

import io.micrometer.observation.ObservationRegistry;
import java.util.Set;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class OpenAiChatGatewayClient implements AiGatewayClient {

    private static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com";

    @Override
    public Set<AiProviderType> supportedProviderTypes() {
        return Set.of(AiProviderType.OPENAI, AiProviderType.OPENAI_COMPATIBLE);
    }

    @Override
    public ChatTestResult testChat(ChatTestCommand command) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(resolveBaseUrl(command))
                .apiKey(command.apiKey())
                .build();
        OpenAiChatOptions options = buildOptions(command);
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.defaultInstance())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();

        ChatResponse response = chatModel.call(new Prompt(command.prompt(), options));
        String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
        return new ChatTestResult(text, null, null, null);
    }

    private OpenAiChatOptions buildOptions(ChatTestCommand command) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(command.modelCode())
                .maxCompletionTokens(command.maxOutputTokens());
        if (command.temperature() != null) {
            builder.temperature(command.temperature().doubleValue());
        }
        if (command.topP() != null) {
            builder.topP(command.topP().doubleValue());
        }
        if (command.frequencyPenalty() != null) {
            builder.frequencyPenalty(command.frequencyPenalty().doubleValue());
        }
        if (command.presencePenalty() != null) {
            builder.presencePenalty(command.presencePenalty().doubleValue());
        }
        if (command.reasoningEffort() != null && !command.reasoningEffort().isBlank()) {
            builder.reasoningEffort(command.reasoningEffort());
        }
        return builder.build();
    }

    private String resolveBaseUrl(ChatTestCommand command) {
        if (command.providerType() == AiProviderType.OPENAI
                && (command.baseUrl() == null
                        || command.baseUrl().isBlank()
                        || "OFFICIAL_DEFAULT".equals(command.baseUrl()))) {
            return OPENAI_DEFAULT_BASE_URL;
        }
        if (command.baseUrl() == null || command.baseUrl().isBlank() || "OFFICIAL_DEFAULT".equals(command.baseUrl())) {
            throw new IllegalArgumentException("OpenAI 兼容供应商 baseUrl 不能为空");
        }
        return command.baseUrl();
    }
}
