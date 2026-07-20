package com.aiplatform.infra.springai.model.provider.mistral;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Mistral AI ChatModel 工厂，避免把 Mistral 私有 options 塞进 OpenAI-compatible 分支。
 */
@Component
public class MistralAiChatModelFactory implements SpringAiChatModelFactory {
    private final SecureModelHttpTransport transport;

    public MistralAiChatModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return MistralAiSpringAiApiFactory.supports(request);
    }

    @Override
    public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
        if (!(options instanceof MistralAiChatOptions mistralOptions)) {
            throw new IllegalArgumentException("Mistral AI ChatModel 需要 MistralAiChatOptions");
        }
        return MistralAiChatModel.builder()
                .mistralAiApi(MistralAiSpringAiApiFactory.create(request, transport))
                .options(mistralOptions)
                .retryTemplate(singleAttemptRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private static RetryTemplate singleAttemptRetryTemplate() {
        return new RetryTemplate(RetryPolicy.withMaxRetries(0));
    }
}
