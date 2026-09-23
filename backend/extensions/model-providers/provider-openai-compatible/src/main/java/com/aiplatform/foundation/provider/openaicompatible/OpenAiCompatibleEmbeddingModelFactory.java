package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.runtime.port.out.EmbeddingModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.provider.springai.SpringAiEmbeddingModelAdapter;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.time.Duration;
import java.util.Objects;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

/**
 * 使用 Spring AI 构造开放兼容协议的嵌入运行时。
 */
public final class OpenAiCompatibleEmbeddingModelFactory implements EmbeddingModelFactory {

    private static final String ARK_MULTIMODAL_EMBEDDING_MODEL =
            "doubao-embedding-vision-251215";

    private final SecretResolver secrets;
    private final OutboundEndpointPolicy endpointPolicy;
    private final Duration timeout;

    public OpenAiCompatibleEmbeddingModelFactory(
            SecretResolver secrets,
            OutboundEndpointPolicy endpointPolicy,
            Duration timeout) {
        this.secrets = Objects.requireNonNull(secrets, "密钥解析器不能为空");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
        this.timeout = requireTimeout(timeout);
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OPENAI_COMPATIBLE;
    }

    @Override
    public EmbeddingModelPort create(ModelRuntimeSpecification specification) {
        try (var secret = secrets.resolve(new SecretReference(
                specification.credentialReference()))) {
            return create(specification, secret);
        }
    }

    /**
     * 受控模型配置调用直接消费当前回调内的凭据，避免通过长期密钥引用再次解析。
     */
    @Override
    public EmbeddingModelPort create(
            ModelRuntimeSpecification specification,
            com.aiplatform.foundation.security.secret.SecretValue credential) {
        requireSpecification(specification);
        endpointPolicy.verify(specification.endpoint());
        return credential.use(chars -> {
            var deployment = new EmbeddingDeploymentRef(
                    specification.deploymentId(),
                    specification.deploymentRevision(),
                    specification.embeddingDimension());
            if (ARK_MULTIMODAL_EMBEDDING_MODEL.equals(specification.modelCode())) {
                return new ArkMultimodalEmbeddingModel(
                        specification.endpoint(),
                        new String(chars),
                        specification.modelCode(),
                        timeout,
                        deployment);
            }
            var options = OpenAiEmbeddingOptions.builder()
                    .baseUrl(specification.endpoint().toString())
                    .apiKey(new String(chars))
                    .model(specification.modelCode())
                    .timeout(timeout)
                    .maxRetries(0)
                    .dimensions(specification.embeddingDimension())
                    .build();
            var model = OpenAiEmbeddingModel.builder()
                    .options(options)
                    .build();
            return new SpringAiEmbeddingModelAdapter(
                    model,
                    deployment);
        });
    }

    private void requireSpecification(ModelRuntimeSpecification specification) {
        Objects.requireNonNull(specification, "模型运行时配置不能为空");
        if (specification.providerKind() != providerKind()
                || specification.modelType() != ModelType.EMBEDDING) {
            throw new IllegalArgumentException("开放兼容嵌入模型配置类型不匹配");
        }
    }

    private static Duration requireTimeout(Duration value) {
        Objects.requireNonNull(value, "模型调用超时不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("模型调用超时必须大于零");
        }
        return value;
    }
}
