package com.aiplatform.infra.springai.model.provider.azureopenai;

import com.aiplatform.infra.springai.model.provider.azureopenai.support.AzureOpenAiProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Objects;

/**
 * Azure OpenAI Spring AI client 工厂，约束 endpoint、api-key 和 api-version。
 */
final class AzureOpenAiSpringAiClientFactory {
    private AzureOpenAiSpringAiClientFactory() {
    }

    static boolean supports(SpringAiModelRuntimeRequest request) {
        return AzureOpenAiProviderSupport.supports(request);
    }

    static void validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        if (!supports(request)) {
            throw new IllegalArgumentException("Azure OpenAI 模型供应商类型必须是 azure-openai");
        }
        if (!AzureOpenAiProviderSupport.AUTH_TYPE_API_KEY.equalsIgnoreCase(request.authType())) {
            throw new IllegalArgumentException("Azure OpenAI 模型供应商仅支持 api_key 鉴权");
        }
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Azure OpenAI 模型供应商密钥未配置");
        }
        AzureOpenAiProviderSupport.resourceEndpoint(request.endpoint());
    }

    static String azureResourceEndpoint(String endpoint) {
        return AzureOpenAiProviderSupport.resourceEndpoint(endpoint);
    }
}
