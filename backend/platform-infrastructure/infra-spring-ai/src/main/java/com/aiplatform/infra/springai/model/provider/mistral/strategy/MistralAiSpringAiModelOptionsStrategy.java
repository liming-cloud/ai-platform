package com.aiplatform.infra.springai.model.provider.mistral.strategy;

import com.aiplatform.infra.springai.model.provider.mistral.support.MistralAiProviderSupport;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Mistral AI provider options 策略，隔离 safePrompt、randomSeed 和 encodingFormat 等 Mistral 私有参数。
 */
@Component
@Order(0)
public class MistralAiSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "REASONING", "EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "temperature",
            "topP",
            "maxTokens",
            "frequencyPenalty",
            "presencePenalty",
            "stopSequences",
            "dimensions",
            "safePrompt",
            "randomSeed",
            "n",
            "encodingFormat"
    );

    @Override
    public String name() {
        return "mistral-ai-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return MistralAiProviderSupport.supports(request) && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前 Mistral AI 策略不支持 providerKind 或模型类型");
        }
        if (!MistralAiProviderSupport.AUTH_TYPE_API_KEY.equalsIgnoreCase(request.authType())) {
            violations.add("Mistral AI provider 必须使用 api_key 鉴权");
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("Mistral AI 不支持的模型 options 字段 " + key);
            }
        }
        ModelOptionValues.validateDoubleRange(request.options(), "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "topP", 0, 1, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "frequencyPenalty", -2, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "presencePenalty", -2, 2, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "maxTokens", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "dimensions", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "randomSeed", 0, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "n", 1, violations);
        ModelOptionValues.validateBooleanOption(request.options(), "safePrompt", violations);
        ModelOptionValues.validateStringOption(request.options(), "encodingFormat", violations);
        ModelOptionValues.validateStringList(request.options(), "stopSequences", violations);
        return violations.isEmpty()
                ? SpringAiModelValidationResult.passed()
                : SpringAiModelValidationResult.failed(violations);
    }

    @Override
    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Mistral AI 模型 options 校验失败：" + validation.summary());
        }
        requireRuntimeCredentialMaterial(request);
        if ("EMBEDDING".equals(normalizedType(request))) {
            MistralAiEmbeddingOptions.Builder builder = MistralAiEmbeddingOptions.builder()
                    .model(request.modelCode());
            ModelOptionValues.stringValue(request.options(), "encodingFormat").ifPresent(builder::encodingFormat);
            return builder.build();
        }
        MistralAiChatOptions.Builder builder = MistralAiChatOptions.builder()
                .model(request.modelCode());
        ModelOptionValues.doubleValue(request.options(), "temperature").ifPresent(builder::temperature);
        ModelOptionValues.doubleValue(request.options(), "topP").ifPresent(builder::topP);
        ModelOptionValues.integerValue(request.options(), "maxTokens").ifPresent(builder::maxTokens);
        ModelOptionValues.doubleValue(request.options(), "frequencyPenalty").ifPresent(builder::frequencyPenalty);
        ModelOptionValues.doubleValue(request.options(), "presencePenalty").ifPresent(builder::presencePenalty);
        ModelOptionValues.stringListValue(request.options(), "stopSequences").ifPresent(builder::stop);
        ModelOptionValues.booleanValue(request.options(), "safePrompt").ifPresent(builder::safePrompt);
        ModelOptionValues.integerValue(request.options(), "randomSeed").ifPresent(builder::randomSeed);
        ModelOptionValues.integerValue(request.options(), "n").ifPresent(builder::n);
        return builder.build();
    }

    private static void requireRuntimeCredentialMaterial(SpringAiModelRuntimeRequest request) {
        if (request.credentialMaterial() == null || request.credentialMaterial().isBlank()) {
            throw new IllegalArgumentException("Mistral AI provider 必须配置 API Key");
        }
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
