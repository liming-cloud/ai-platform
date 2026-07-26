package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.api.extension.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.api.invocation.ChatModelPort;
import com.aiplatform.foundation.modelruntime.api.model.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.api.model.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.provider.springai.SpringAiChatModelAdapter;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.secret.SecretResolver;
import java.time.Duration;
import java.util.Objects;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 使用 Spring AI 构造开放兼容协议的对话运行时。
 */
public final class OpenAiCompatibleChatModelFactory implements ChatModelFactory {

    private final SecretResolver secrets;
    private final OutboundEndpointPolicy endpointPolicy;
    private final Duration timeout;

    public OpenAiCompatibleChatModelFactory(
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
    public ChatModelPort create(ModelRuntimeSpecification specification) {
        requireSpecification(specification, ModelType.CHAT);
        endpointPolicy.verify(specification.endpoint());
        try (var secret = secrets.resolve(specification.credentialReference())) {
            return secret.use(chars -> {
                var options = OpenAiChatOptions.builder()
                        .baseUrl(specification.endpoint().toString())
                        .apiKey(new String(chars))
                        .model(specification.modelCode())
                        .timeout(timeout)
                        .maxRetries(0)
                        .maxTokens(specification.outputLimit())
                        .parallelToolCalls(false)
                        .build();
                var model = OpenAiChatModel.builder()
                        .options(options)
                        .build();
                return new SpringAiChatModelAdapter(
                        model,
                        new ChatDeploymentRef(
                                specification.deploymentId(),
                                specification.deploymentRevision()));
            });
        }
    }

    private void requireSpecification(
            ModelRuntimeSpecification specification,
            ModelType expectedType) {
        Objects.requireNonNull(specification, "模型运行时配置不能为空");
        if (specification.providerKind() != providerKind()
                || specification.modelType() != expectedType) {
            throw new IllegalArgumentException("开放兼容对话模型配置类型不匹配");
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
