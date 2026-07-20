package com.aiplatform.infra.springai.model.provider.local;

import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地确定性 EmbeddingModel 工厂，支持无外部依赖的向量运行时闭环。
 */
@Component
@ConditionalOnProperty(name = "aip.model-hub.local-deterministic-enabled", havingValue = "true")
public class LocalDeterministicEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    public static final String PROVIDER_KIND = "local-deterministic";

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return PROVIDER_KIND.equals(normalizedProviderKind(request))
                && "EMBEDDING".equals(normalizedModelType(request));
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        if (!supports(request)) {
            throw new IllegalArgumentException("本地确定性运行时仅支持 EMBEDDING 模型");
        }
        LocalDeterministicRuntimeContract.validate(request);
        EmbeddingOptions requiredOptions = Objects.requireNonNull(options, "本地 EmbeddingOptions 不能为空");
        int dimension = LocalDeterministicRuntimeContract.requireConsistentEmbeddingDimensions(
                request.embeddingDimension(),
                requiredOptions.getDimensions()
        );
        return new LocalDeterministicEmbeddingModel(dimension);
    }

    private static String normalizedProviderKind(SpringAiModelRuntimeRequest request) {
        return request.providerKind().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedModelType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
