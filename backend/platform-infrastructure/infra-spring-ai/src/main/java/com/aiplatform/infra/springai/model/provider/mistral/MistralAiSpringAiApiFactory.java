package com.aiplatform.infra.springai.model.provider.mistral;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.provider.mistral.support.MistralAiProviderSupport;
import java.util.Objects;
import org.springframework.ai.mistralai.api.MistralAiApi;

/**
 * Mistral AI Spring AI API 工厂，约束 provider kind、鉴权方式和 endpoint 归一化。
 */
final class MistralAiSpringAiApiFactory {
    private MistralAiSpringAiApiFactory() {
    }

    static boolean supports(SpringAiModelRuntimeRequest request) {
        return MistralAiProviderSupport.supports(request);
    }

    static MistralAiApi create(SpringAiModelRuntimeRequest request, SecureModelHttpTransport transport) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
        if (!supports(request)) {
            throw new IllegalArgumentException("Mistral AI 模型供应商类型必须是 mistral-ai");
        }
        if (!MistralAiProviderSupport.AUTH_TYPE_API_KEY.equalsIgnoreCase(request.authType())) {
            throw new IllegalArgumentException("Mistral AI 模型供应商仅支持 api_key 鉴权");
        }
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Mistral AI 模型供应商密钥未配置");
        }
        String safeEndpoint = transport.validateEndpoint(request.endpoint()).toString();
        return MistralAiApi.builder()
                .baseUrl(springAiBaseUrl(safeEndpoint))
                .apiKey(request.credentialMaterial())
                .restClientBuilder(transport.restClientBuilder())
                .webClientBuilder(transport.webClientBuilder())
                .build();
    }

    private static String springAiBaseUrl(String endpoint) {
        String normalized = endpoint.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("/v1") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }

}
