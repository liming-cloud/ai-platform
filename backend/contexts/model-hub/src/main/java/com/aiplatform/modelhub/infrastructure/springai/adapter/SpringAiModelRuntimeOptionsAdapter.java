package com.aiplatform.modelhub.infrastructure.springai.adapter;

import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.infra.springai.model.SpringAiModelRuntimeGateway;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderAuthTypes;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * model-hub 到 Spring AI 的模型运行时适配器，负责凭证材料解析、options 校验和连通性探测。
 */
@Component
public class SpringAiModelRuntimeOptionsAdapter implements ModelRuntimeOptionsPort {
    private final SpringAiModelRuntimeGateway gateway;
    private final SpringAiConnectivityCredentialResolver credentialResolver;
    private final ModelRuntimeAuthorityDigest authorityDigest;

    public SpringAiModelRuntimeOptionsAdapter(
            SpringAiModelRuntimeGateway gateway,
            SpringAiConnectivityCredentialResolver credentialResolver,
            ModelRuntimeAuthorityDigest authorityDigest
    ) {
        this.gateway = Objects.requireNonNull(gateway, "Spring AI 模型运行时网关不能为空");
        this.credentialResolver = Objects.requireNonNull(
                credentialResolver,
                "Spring AI 连通性探测凭证解析器不能为空"
        );
        this.authorityDigest = Objects.requireNonNull(
                authorityDigest,
                "模型运行时 authority 摘要服务不能为空"
        );
    }

    @Override
    public ModelRuntimeValidation validate(ModelProvider provider, ModelConfig config) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(config, "模型配置不能为空");
        try {
            SpringAiModelValidationResult result = gateway.validate(toRequest(provider, config, null));
            return result.valid()
                    ? ModelRuntimeValidation.passed()
                    : ModelRuntimeValidation.failed(result.violations());
        } catch (IllegalArgumentException exception) {
            return ModelRuntimeValidation.failed(java.util.List.of(exception.getMessage()));
        }
    }

    @Override
    public ModelRuntimeConnectivity testConnectivity(ModelProvider provider, ModelConfig config) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(config, "模型配置不能为空");
        SpringAiModelConnectivityResult result;
        try {
            String credentialMaterial = loadCredentialMaterial(provider, config);
            result = gateway.testConnectivity(toRequest(provider, config, credentialMaterial));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            result = SpringAiModelConnectivityResult.failed("模型运行时配置不合法：" + exception.getMessage());
        }
        return new ModelRuntimeConnectivity(
                result.connected(),
                ModelConnectivityStatus.valueOf(result.status()),
                result.message()
        );
    }

    private String loadCredentialMaterial(ModelProvider provider, ModelConfig config) {
        String secretRefId = null;
        if (!ModelProviderAuthTypes.isNoAuth(provider.authType())) {
            ModelSecretRef secretRef = Objects.requireNonNull(
                    provider.secretRef(),
                    "模型供应商凭证材料引用不能为空"
            );
            secretRefId = secretRef.secretId();
        }
        Map<String, Object> effectiveOptions = runtimeOptions(config);
        String credentialMaterial = credentialResolver.resolveProbeCredentialMaterial(
                new SpringAiRuntimeSecretResolutionRequest(
                        provider.tenantId().value(),
                        config.modelConfigId(),
                        provider.providerId(),
                        provider.providerCode(),
                        providerKind(config),
                        provider.endpoint(),
                        provider.authType(),
                        config.modelCode(),
                        config.version(),
                        config.modelType().name(),
                        effectiveOptions,
                        authorityDigest.digest(effectiveOptions),
                        secretRefId
                )
        );
        if (!ModelProviderAuthTypes.isNoAuth(provider.authType())) {
            return requireText(credentialMaterial, "模型供应商凭证材料");
        }
        if (credentialMaterial != null) {
            throw new IllegalStateException("no_auth 模型运行时凭证解析结果必须为空");
        }
        return null;
    }

    private static SpringAiModelRuntimeRequest toRequest(
            ModelProvider provider,
            ModelConfig config,
            String credentialMaterial
    ) {
        return new SpringAiModelRuntimeRequest(
                provider.providerCode(),
                providerKind(config),
                provider.endpoint(),
                provider.authType(),
                config.modelCode(),
                config.modelType().name(),
                config.contextWindow(),
                config.embeddingDimension(),
                runtimeOptions(config),
                credentialMaterial
        );
    }

    private static String providerKind(ModelConfig config) {
        return config.options().providerKind();
    }

    private static Map<String, Object> runtimeOptions(ModelConfig config) {
        return config.options().springAiOptions();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
