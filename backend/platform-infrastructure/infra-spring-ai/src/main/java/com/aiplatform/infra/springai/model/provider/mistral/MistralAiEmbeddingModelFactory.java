package com.aiplatform.infra.springai.model.provider.mistral;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.mistralai.MistralAiEmbeddingModel;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Mistral AI EmbeddingModel 工厂，按 provider 运行时请求生成模型实例。
 */
@Component
public class MistralAiEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    private final SecureModelHttpTransport transport;

    public MistralAiEmbeddingModelFactory(SecureModelHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return MistralAiSpringAiApiFactory.supports(request);
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        if (!(options instanceof MistralAiEmbeddingOptions mistralOptions)) {
            throw new IllegalArgumentException("Mistral AI EmbeddingModel 需要 MistralAiEmbeddingOptions");
        }
        return MistralAiEmbeddingModel.builder()
                .mistralAiApi(MistralAiSpringAiApiFactory.create(request, transport))
                .metadataMode(MetadataMode.EMBED)
                .options(mistralOptions)
                .retryTemplate(singleAttemptRetryTemplate())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private static RetryTemplate singleAttemptRetryTemplate() {
        return new RetryTemplate(RetryPolicy.withMaxRetries(0));
    }
}
