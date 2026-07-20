package com.aiplatform.infra.springai.model.provider.ollama;

import com.aiplatform.infra.springai.http.SecureModelHttpTransport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.ollama.api.OllamaApi;

/**
 * Ollama Spring AI API 工厂，约束 provider kind、鉴权方式和 endpoint 归一化。
 */
final class OllamaSpringAiApiFactory {
    private static final String PROVIDER_KIND = "ollama";
    private static final String AUTH_TYPE_NO_AUTH = "no_auth";

    private OllamaSpringAiApiFactory() {
    }

    static boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return PROVIDER_KIND.equals(normalize(request.providerKind()));
    }

    static OllamaApi create(SpringAiModelRuntimeRequest request, SecureModelHttpTransport transport) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        Objects.requireNonNull(transport, "模型安全 HTTP 传输不能为空");
        if (!supports(request)) {
            throw new IllegalArgumentException("Ollama 模型供应商类型必须是 ollama");
        }
        if (!AUTH_TYPE_NO_AUTH.equals(normalize(request.authType()))) {
            throw new IllegalArgumentException("Ollama 模型供应商仅支持 no_auth 鉴权");
        }
        if (request.credentialMaterial() != null && !request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Ollama 模型供应商不允许携带 API Key");
        }
        String safeEndpoint = transport.validateEndpoint(request.endpoint()).toString();
        return OllamaApi.builder()
                .baseUrl(springAiBaseUrl(safeEndpoint))
                .restClientBuilder(transport.restClientBuilder())
                .webClientBuilder(transport.webClientBuilder())
                .build();
    }

    private static String springAiBaseUrl(String endpoint) {
        String normalized = endpoint.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("/api") ? normalized.substring(0, normalized.length() - 4) : normalized;
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "运行时字段不能为空").trim().toLowerCase(Locale.ROOT);
    }
}
