package com.aiplatform.infra.springai.model.provider.openai;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * OpenAI-compatible Spring AI API 工厂，集中处理 provider kind 与 endpoint 归一化。
 */
final class OpenAiCompatibleSpringAiApiFactory {
    private static final String AUTH_TYPE_API_KEY = "api_key";
    private static final Set<String> SUPPORTED_PROVIDER_KINDS = Set.of("openai-compatible", "openai");

    private OpenAiCompatibleSpringAiApiFactory() {
    }

    static boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        String providerKind = request.providerKind().trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_PROVIDER_KINDS.contains(providerKind);
    }

    static void validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        if (!supports(request)) {
            throw new IllegalArgumentException("OpenAI-compatible 模型供应商类型必须是 openai-compatible 或 openai");
        }
        if (!AUTH_TYPE_API_KEY.equalsIgnoreCase(request.authType())) {
            throw new IllegalArgumentException("OpenAI-compatible 模型供应商仅支持 api_key 鉴权");
        }
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("OpenAI-compatible 模型供应商密钥未配置");
        }
    }

    static String baseUrl(String endpoint) {
        String trimmed = endpoint.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
