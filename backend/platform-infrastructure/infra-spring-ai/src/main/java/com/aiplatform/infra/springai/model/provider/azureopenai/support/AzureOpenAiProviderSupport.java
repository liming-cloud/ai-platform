package com.aiplatform.infra.springai.model.provider.azureopenai.support;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.openai.azure.AzureOpenAIServiceVersion;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Azure OpenAI provider 共享规则，集中约束 provider kind、deployment 和 api-version。
 */
public final class AzureOpenAiProviderSupport {
    public static final String AUTH_TYPE_API_KEY = "api_key";
    public static final String DEPLOYMENT_NAME_OPTION = "deploymentName";
    public static final String API_VERSION_OPTION = "apiVersion";
    public static final String SERVICE_VERSION_OPTION = "serviceVersion";

    private static final Set<String> SUPPORTED_PROVIDER_KINDS = Set.of(
            "azure-openai",
            "azure_openai",
            "azureopenai"
    );

    private AzureOpenAiProviderSupport() {
    }

    public static boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return SUPPORTED_PROVIDER_KINDS.contains(normalizeProviderKind(request.providerKind()));
    }

    public static String deploymentName(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return textOption(request.options(), DEPLOYMENT_NAME_OPTION)
                .orElseThrow(() -> new IllegalArgumentException("Azure OpenAI deploymentName 不能为空"));
    }

    public static AzureOpenAIServiceVersion serviceVersion(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        Optional<String> configured = textOption(request.options(), API_VERSION_OPTION)
                .or(() -> textOption(request.options(), SERVICE_VERSION_OPTION));
        return configured.map(AzureOpenAiProviderSupport::parseServiceVersion)
                .orElseThrow(() -> new IllegalArgumentException("Azure OpenAI api-version 不能为空"));
    }

    public static AzureOpenAIServiceVersion parseServiceVersion(String value) {
        String normalized = Objects.requireNonNull(value, "Azure OpenAI api-version 不能为空").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Azure OpenAI api-version 不能为空");
        }
        return AzureOpenAIServiceVersion.fromString(normalized);
    }

    public static Optional<String> textOption(Map<String, Object> options, String key) {
        Object value = options.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text.trim());
        }
        return Optional.empty();
    }

    public static String resourceEndpoint(String endpoint) {
        String normalized = Objects.requireNonNull(endpoint, "Azure OpenAI endpoint 不能为空").trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalArgumentException("Azure OpenAI endpoint 必须使用 HTTPS");
        }
        return normalized.endsWith("/openai")
                ? normalized.substring(0, normalized.length() - "/openai".length())
                : normalized;
    }

    private static String normalizeProviderKind(String value) {
        return Objects.requireNonNull(value, "供应商类型不能为空").trim().toLowerCase(Locale.ROOT);
    }
}
