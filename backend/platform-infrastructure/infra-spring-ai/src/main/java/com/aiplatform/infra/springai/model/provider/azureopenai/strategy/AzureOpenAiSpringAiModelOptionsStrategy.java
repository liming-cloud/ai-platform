package com.aiplatform.infra.springai.model.provider.azureopenai.strategy;

import com.aiplatform.infra.springai.model.provider.azureopenai.support.AzureOpenAiProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Azure OpenAI provider options 策略，使用 deploymentName/api-version 语义替代 OpenAI-compatible URL 拼接。
 */
@Component
@Order(0)
public class AzureOpenAiSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "REASONING", "EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "temperature",
            "topP",
            "maxTokens",
            "frequencyPenalty",
            "presencePenalty",
            "stopSequences",
            "dimensions",
            "deploymentName",
            "apiVersion",
            "serviceVersion",
            "user",
            "reasoningEffort"
    );

    @Override
    public String name() {
        return "azure-openai-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return AzureOpenAiProviderSupport.supports(request) && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前 Azure OpenAI 策略不支持 providerKind 或模型类型");
        }
        if (!AzureOpenAiProviderSupport.AUTH_TYPE_API_KEY.equalsIgnoreCase(request.authType())) {
            violations.add("Azure OpenAI provider 必须使用 api_key 鉴权");
        }
        try {
            AzureOpenAiProviderSupport.resourceEndpoint(request.endpoint());
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("Azure OpenAI 不支持的模型 options 字段 " + key);
            }
        }
        ModelOptionValues.validateRequiredStringOption(
                request.options(),
                AzureOpenAiProviderSupport.DEPLOYMENT_NAME_OPTION,
                "Azure OpenAI deploymentName",
                violations
        );
        validateServiceVersion(request.options(), violations);
        ModelOptionValues.validateStringOption(request.options(), "user", violations);
        ModelOptionValues.validateStringOption(request.options(), "reasoningEffort", violations);
        ModelOptionValues.validateDoubleRange(request.options(), "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "topP", 0, 1, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "frequencyPenalty", -2, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "presencePenalty", -2, 2, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "maxTokens", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "dimensions", 1, violations);
        ModelOptionValues.validateStringList(request.options(), "stopSequences", violations);
        if ("EMBEDDING".equals(normalizedType(request))
                && request.embeddingDimension() == null
                && !request.options().containsKey("dimensions")) {
            violations.add("Azure OpenAI 向量模型必须配置 embeddingDimension 或 dimensions");
        }
        return violations.isEmpty()
                ? SpringAiModelValidationResult.passed()
                : SpringAiModelValidationResult.failed(violations);
    }

    @Override
    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Azure OpenAI 模型 options 校验失败：" + validation.summary());
        }
        requireRuntimeCredentialMaterial(request);
        String deploymentName = AzureOpenAiProviderSupport.deploymentName(request);
        if ("EMBEDDING".equals(normalizedType(request))) {
            OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder();
            builder.baseUrl(azureResourceEndpoint(request.endpoint()));
            builder.apiKey(request.credentialMaterial());
            builder.model(request.modelCode());
            builder.azure(true);
            builder.deploymentName(deploymentName);
            builder.azureOpenAIServiceVersion(AzureOpenAiProviderSupport.serviceVersion(request));
            builder.maxRetries(0);
            ModelOptionValues.integerValue(request.options(), "dimensions")
                    .or(() -> Optional.ofNullable(request.embeddingDimension()))
                    .ifPresent(builder::dimensions);
            ModelOptionValues.stringValue(request.options(), "user").ifPresent(builder::user);
            return builder.build();
        }
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder();
        builder.baseUrl(azureResourceEndpoint(request.endpoint()));
        builder.apiKey(request.credentialMaterial());
        builder.model(request.modelCode());
        builder.azure(true);
        builder.deploymentName(deploymentName);
        builder.azureOpenAIServiceVersion(AzureOpenAiProviderSupport.serviceVersion(request));
        builder.maxRetries(0);
        ModelOptionValues.doubleValue(request.options(), "temperature").ifPresent(builder::temperature);
        ModelOptionValues.doubleValue(request.options(), "topP").ifPresent(builder::topP);
        ModelOptionValues.integerValue(request.options(), "maxTokens").ifPresent(builder::maxTokens);
        ModelOptionValues.doubleValue(request.options(), "frequencyPenalty").ifPresent(builder::frequencyPenalty);
        ModelOptionValues.doubleValue(request.options(), "presencePenalty").ifPresent(builder::presencePenalty);
        ModelOptionValues.stringListValue(request.options(), "stopSequences").ifPresent(builder::stop);
        ModelOptionValues.stringValue(request.options(), "user").ifPresent(builder::user);
        ModelOptionValues.stringValue(request.options(), "reasoningEffort").ifPresent(builder::reasoningEffort);
        return builder.build();
    }

    private static void requireRuntimeCredentialMaterial(SpringAiModelRuntimeRequest request) {
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Azure OpenAI provider 必须配置 API Key");
        }
    }

    private static String azureResourceEndpoint(String endpoint) {
        return AzureOpenAiProviderSupport.resourceEndpoint(endpoint);
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }

    private static void validateServiceVersion(Map<String, Object> options, List<String> violations) {
        int violationCountBeforeRead = violations.size();
        Optional<String> apiVersion = readStringOption(options, AzureOpenAiProviderSupport.API_VERSION_OPTION, violations);
        Optional<String> serviceVersion = readStringOption(options, AzureOpenAiProviderSupport.SERVICE_VERSION_OPTION, violations);
        if (violations.size() > violationCountBeforeRead) {
            return;
        }
        if (apiVersion.isEmpty() && serviceVersion.isEmpty()) {
            violations.add("Azure OpenAI api-version 不能为空");
            return;
        }
        if (apiVersion.isPresent() && serviceVersion.isPresent() && !apiVersion.get().equals(serviceVersion.get())) {
            violations.add("Azure OpenAI apiVersion 与 serviceVersion 不能同时配置为不同值");
            return;
        }
        apiVersion.or(() -> serviceVersion).ifPresent(value -> {
            try {
                AzureOpenAiProviderSupport.parseServiceVersion(value);
            } catch (IllegalArgumentException exception) {
                violations.add(exception.getMessage());
            }
        });
    }

    private static Optional<String> readStringOption(
            Map<String, Object> options,
            String key,
            List<String> violations
    ) {
        try {
            return ModelOptionValues.stringValue(options, key);
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
            return Optional.empty();
        }
    }

}
