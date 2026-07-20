package com.aiplatform.infra.springai.model.provider.local;

import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地确定性 ChatModel 工厂，支撑无外部 API Key 的聊天主链路验收。
 */
@Component
@ConditionalOnProperty(name = "aip.model-hub.local-deterministic-enabled", havingValue = "true")
public class LocalDeterministicChatModelFactory implements SpringAiChatModelFactory {
    public static final String PROVIDER_KIND = LocalDeterministicEmbeddingModelFactory.PROVIDER_KIND;

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return PROVIDER_KIND.equals(normalizedProviderKind(request))
                && "CHAT".equals(normalizedModelType(request));
    }

    @Override
    public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
        if (!supports(request)) {
            throw new IllegalArgumentException("本地确定性运行时仅支持 CHAT 模型");
        }
        LocalDeterministicRuntimeContract.validate(request);
        return new LocalDeterministicChatModel(options);
    }

    private static String normalizedProviderKind(SpringAiModelRuntimeRequest request) {
        return request.providerKind().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedModelType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
