package com.aiplatform.infra.springai.model.provider.bedrock;

import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.stereotype.Component;

/**
 * Amazon Bedrock ChatModel 工厂，使用 Bedrock Converse 原生 Spring AI 模型。
 */
@Component
public class BedrockChatModelFactory implements SpringAiChatModelFactory {
    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return BedrockSpringAiClientFactory.supports(request);
    }

    @Override
    public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
        if (!(options instanceof BedrockChatOptions bedrockOptions)) {
            throw new IllegalArgumentException("Amazon Bedrock ChatModel 需要 BedrockChatOptions");
        }
        return new BedrockProxyChatModel(
                BedrockSpringAiClientFactory.bedrockRuntimeClient(request),
                BedrockSpringAiClientFactory.bedrockRuntimeAsyncClient(request),
                bedrockOptions,
                ObservationRegistry.NOOP,
                ToolCallingManager.builder().build()
        );
    }
}
