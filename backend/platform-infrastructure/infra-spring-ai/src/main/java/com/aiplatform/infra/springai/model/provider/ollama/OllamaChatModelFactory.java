package com.aiplatform.infra.springai.model.provider.ollama;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Ollama ChatModel 工厂，限定 no_auth 本地或私有化部署运行时。
 */
@Component
public class OllamaChatModelFactory implements SpringAiChatModelFactory {
    private final SecureModelHttpTransport transport;
    private final RetryTemplate retryTemplate;

    @Autowired
    public OllamaChatModelFactory(SecureModelHttpTransport transport) {
        this(transport, RetryUtils.DEFAULT_RETRY_TEMPLATE);
    }

    OllamaChatModelFactory(SecureModelHttpTransport transport, RetryTemplate retryTemplate) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
        this.retryTemplate = Objects.requireNonNull(retryTemplate, "Ollama 重试模板不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return OllamaSpringAiApiFactory.supports(request);
    }

    @Override
    public ChatModel create(SpringAiModelRuntimeRequest request, ChatOptions options) {
        if (!(options instanceof OllamaChatOptions ollamaOptions)) {
            throw new IllegalArgumentException("Ollama ChatModel 需要 OllamaChatOptions");
        }
        return OllamaChatModel.builder()
                .ollamaApi(OllamaSpringAiApiFactory.create(request, transport))
                .options(ollamaOptions)
                .observationRegistry(ObservationRegistry.NOOP)
                .modelManagementOptions(ModelManagementOptions.defaults())
                .retryTemplate(retryTemplate)
                .build();
    }
}
