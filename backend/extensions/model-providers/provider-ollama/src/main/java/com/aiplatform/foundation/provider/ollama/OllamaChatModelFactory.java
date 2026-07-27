package com.aiplatform.foundation.provider.ollama;

import com.aiplatform.foundation.modelruntime.application.runtime.port.out.ChatModelFactory;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.ChatModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.chat.ChatDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.provider.springai.SpringAiChatModelAdapter;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import java.util.Objects;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * 使用 Spring AI 构造 Ollama 对话运行时。
 */
public final class OllamaChatModelFactory implements ChatModelFactory {

    private final OutboundEndpointPolicy endpointPolicy;

    public OllamaChatModelFactory(OutboundEndpointPolicy endpointPolicy) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "出站地址策略不能为空");
    }

    @Override
    public ProviderKind providerKind() {
        return ProviderKind.OLLAMA;
    }

    @Override
    public ChatModelPort create(ModelRuntimeSpecification specification) {
        Objects.requireNonNull(specification, "模型运行时配置不能为空");
        if (specification.providerKind() != providerKind()
                || specification.modelType() != ModelType.CHAT) {
            throw new IllegalArgumentException("Ollama 对话模型配置类型不匹配");
        }
        endpointPolicy.verify(specification.endpoint());
        var api = OllamaApi.builder()
                .baseUrl(specification.endpoint().toString())
                .build();
        var options = OllamaChatOptions.builder()
                .model(specification.modelCode())
                .numCtx(specification.contextWindow())
                .numPredict(specification.outputLimit())
                .build();
        var model = OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options)
                .build();
        return new SpringAiChatModelAdapter(
                model,
                new ChatDeploymentRef(
                        specification.deploymentId(),
                        specification.deploymentRevision()));
    }
}
