package com.aiplatform.infra.springai.model.strategy;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.stereotype.Component;

/**
 * Spring AI 标准模型 options 策略，只作为未知扩展 provider 的兜底。
 * 平台内建 provider 必须由其专用策略声明并校验，禁止在不支持的模型类型上回退到通用 options。
 */
@Component
public class StandardSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    public static final String NAME = "standard-spring-ai-options";
    private static final Set<String> DEDICATED_PROVIDER_KINDS = Set.of(
            "openai-compatible", "openai",
            "azure-openai", "azure_openai", "azureopenai",
            "ollama",
            "mistral-ai", "mistral_ai", "mistral",
            "bedrock", "amazon-bedrock", "amazon_bedrock", "aws-bedrock", "aws_bedrock",
            "vertex-ai", "vertex_ai", "vertexai", "google-vertex-ai", "google_vertex_ai",
            "gcp-vertex-ai", "gcp_vertex_ai",
            "local-deterministic"
    );
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "REASONING", "EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "temperature",
            "topP",
            "topK",
            "maxTokens",
            "frequencyPenalty",
            "presencePenalty",
            "stopSequences",
            "dimensions"
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return !DEDICATED_PROVIDER_KINDS.contains(normalizedProviderKind(request))
                && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前策略不支持模型类型 " + request.modelType());
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("不支持的模型 options 字段 " + key);
            }
        }
        ModelOptionValues.validateDoubleRange(request.options(), "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "topP", 0, 1, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "frequencyPenalty", -2, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "presencePenalty", -2, 2, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "topK", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "maxTokens", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "dimensions", 1, violations);
        ModelOptionValues.validateStringList(request.options(), "stopSequences", violations);
        return violations.isEmpty()
                ? SpringAiModelValidationResult.passed()
                : SpringAiModelValidationResult.failed(violations);
    }

    @Override
    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            throw new IllegalArgumentException("模型 options 校验失败：" + validation.summary());
        }
        if ("EMBEDDING".equals(normalizedType(request))) {
            EmbeddingOptions.Builder builder = EmbeddingOptions.builder()
                    .model(request.modelCode());
            ModelOptionValues.integerValue(request.options(), "dimensions")
                    .ifPresent(builder::dimensions);
            return builder.build();
        }
        ChatOptions.Builder builder = ChatOptions.builder().model(request.modelCode());
        ModelOptionValues.doubleValue(request.options(), "temperature").ifPresent(builder::temperature);
        ModelOptionValues.doubleValue(request.options(), "topP").ifPresent(builder::topP);
        ModelOptionValues.integerValue(request.options(), "topK").ifPresent(builder::topK);
        ModelOptionValues.integerValue(request.options(), "maxTokens").ifPresent(builder::maxTokens);
        ModelOptionValues.doubleValue(request.options(), "frequencyPenalty").ifPresent(builder::frequencyPenalty);
        ModelOptionValues.doubleValue(request.options(), "presencePenalty").ifPresent(builder::presencePenalty);
        ModelOptionValues.stringListValue(request.options(), "stopSequences").ifPresent(builder::stopSequences);
        return builder.build();
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizedProviderKind(SpringAiModelRuntimeRequest request) {
        return request.providerKind().trim().toLowerCase(Locale.ROOT);
    }

}
