package com.aiplatform.infra.springai.embedding.adapter;

import com.aiplatform.infra.springai.embedding.EmbeddingClientPort;
import com.aiplatform.infra.springai.embedding.model.EmbeddingRuntimeRequest;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolver;
import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.ProviderErrorSanitizer;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.model.ModelOptions;
import org.springframework.stereotype.Component;

/**
 * 按受控运行时请求创建 Spring AI EmbeddingModel，不读取全局 endpoint 或凭证材料。
 */
@Component
public class SpringAiRuntimeEmbeddingClientAdapter implements EmbeddingClientPort {

    private final SpringAiModelOptionsRegistry optionsRegistry;
    private final List<SpringAiEmbeddingModelFactory> embeddingModelFactories;
    private final SpringAiRuntimeSecretResolver secretResolver;

    public SpringAiRuntimeEmbeddingClientAdapter(
            SpringAiModelOptionsRegistry optionsRegistry,
            List<SpringAiEmbeddingModelFactory> embeddingModelFactories,
            SpringAiRuntimeSecretResolver secretResolver
    ) {
        this.optionsRegistry = Objects.requireNonNull(optionsRegistry, "模型 options 注册表不能为空");
        this.embeddingModelFactories = copyRequiredList(embeddingModelFactories, "EmbeddingModel 工厂");
        this.secretResolver = Objects.requireNonNull(secretResolver, "Spring AI 运行时凭证材料解析器不能为空");
    }

    @Override
    public float[] embed(EmbeddingRuntimeRequest request, String text) {
        Objects.requireNonNull(request, "向量模型运行时请求不能为空");
        requireText(text, "待向量化文本");
        String credentialMaterial = resolveCredentialMaterial(request);
        SpringAiModelRuntimeRequest runtimeRequestWithoutSecret = toModelRuntimeRequest(request, null);
        SpringAiEmbeddingModelFactory factory = selectFactory(runtimeRequestWithoutSecret);
        SpringAiModelRuntimeRequest runtimeRequest = toModelRuntimeRequest(request, credentialMaterial);
        EmbeddingOptions options = embeddingOptions(runtimeRequest);
        try {
            EmbeddingModel embeddingModel = factory.create(runtimeRequest, options);
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(text), options));
            if (response.getResults().isEmpty()) {
                throw new IllegalStateException("Embedding 模型未返回向量结果");
            }
            return validatedEmbeddingVector(response.getResult().getOutput());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Embedding 模型调用失败："
                            + ProviderErrorSanitizer.safeReason(exception, credentialMaterial)
            );
        }
    }

    private EmbeddingOptions embeddingOptions(SpringAiModelRuntimeRequest request) {
        ModelOptions options = optionsRegistry.select(request).buildOptions(request);
        if (options instanceof EmbeddingOptions embeddingOptions) {
            return embeddingOptions;
        }
        throw new IllegalArgumentException("向量模型运行时必须生成 EmbeddingOptions");
    }

    private SpringAiEmbeddingModelFactory selectFactory(SpringAiModelRuntimeRequest request) {
        return embeddingModelFactories.stream()
                .filter(factory -> factory.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Spring AI Embedding 运行时暂不支持供应商类型：" + request.providerKind()
                ));
    }

    private String resolveCredentialMaterial(EmbeddingRuntimeRequest request) {
        String credentialMaterial = secretResolver.resolveCredentialMaterial(
                new SpringAiRuntimeSecretResolutionRequest(
                        request.tenantId(),
                        request.modelConfigId(),
                        request.providerId(),
                        request.providerCode(),
                        request.providerKind(),
                        request.providerEndpoint(),
                        request.authType(),
                        request.modelCode(),
                        request.modelConfigVersion(),
                        request.modelType(),
                        request.effectiveOptions(),
                        request.effectiveOptionsDigest(),
                        request.secretRefId()
                )
        );
        if (request.requiresCredentialMaterial()) {
            return requireText(credentialMaterial, "模型供应商凭证材料");
        }
        if (credentialMaterial != null) {
            throw new IllegalStateException("no_auth 模型运行时凭证解析结果必须为空");
        }
        return null;
    }

    private static SpringAiModelRuntimeRequest toModelRuntimeRequest(
            EmbeddingRuntimeRequest request,
            String credentialMaterial
    ) {
        return new SpringAiModelRuntimeRequest(
                request.providerCode(),
                request.providerKind(),
                request.providerEndpoint(),
                request.authType(),
                request.modelCode(),
                request.modelType(),
                null,
                request.optionAsInteger("dimensions"),
                request.effectiveOptions(),
                credentialMaterial
        );
    }

    private static float[] validatedEmbeddingVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("Embedding 模型返回空向量");
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Embedding 模型返回非法向量值");
            }
        }
        return embedding.clone();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> List<T> copyRequiredList(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream()
                .map(value -> requireElement(value, fieldName))
                .toList();
    }

    private static <T> T requireElement(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
