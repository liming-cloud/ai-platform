package com.aiplatform.infra.springai.model.provider.mistral.support;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Mistral AI provider 共享规则，集中约束 provider kind。
 */
public final class MistralAiProviderSupport {
    public static final String AUTH_TYPE_API_KEY = "api_key";

    private static final Set<String> SUPPORTED_PROVIDER_KINDS = Set.of(
            "mistral-ai",
            "mistral_ai",
            "mistral"
    );

    private MistralAiProviderSupport() {
    }

    public static boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return SUPPORTED_PROVIDER_KINDS.contains(normalize(request.providerKind()));
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "供应商类型不能为空").trim().toLowerCase(Locale.ROOT);
    }
}
