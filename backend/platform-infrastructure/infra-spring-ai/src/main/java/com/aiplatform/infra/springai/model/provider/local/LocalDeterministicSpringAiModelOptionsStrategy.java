package com.aiplatform.infra.springai.model.provider.local;

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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 本地确定性运行时的专用 options 策略。该策略只在显式启用本地能力时注册，
 * 并在进入 factory 前锁定模型类型、options 白名单和向量维度边界。
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "aip.model-hub.local-deterministic-enabled", havingValue = "true")
public class LocalDeterministicSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "EMBEDDING");
    private static final Set<String> CHAT_OPTIONS = Set.of(
            "temperature",
            "topP",
            "topK",
            "maxTokens",
            "frequencyPenalty",
            "presencePenalty",
            "stopSequences"
    );
    private static final Set<String> EMBEDDING_OPTIONS = Set.of("dimensions");

    @Override
    public String name() {
        return "local-deterministic-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return LocalDeterministicEmbeddingModelFactory.PROVIDER_KIND.equals(normalizedProviderKind(request))
                && SUPPORTED_TYPES.contains(normalizedModelType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        String modelType = normalizedModelType(request);
        if (!supports(request)) {
            violations.add("本地确定性 options 策略仅支持 local-deterministic 的 CHAT 或 EMBEDDING 模型");
        }
        validateRuntimeContract(request, violations);
        validateAllowedOptions(request.options(), modelType, violations);
        if ("CHAT".equals(modelType)) {
            validateChatOptions(request.options(), violations);
        } else if ("EMBEDDING".equals(modelType)) {
            validateEmbeddingDimensions(request, violations);
        }
        return violations.isEmpty()
                ? SpringAiModelValidationResult.passed()
                : SpringAiModelValidationResult.failed(violations);
    }

    @Override
    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            throw new IllegalArgumentException("本地确定性模型 options 校验失败：" + validation.summary());
        }
        if ("EMBEDDING".equals(normalizedModelType(request))) {
            Integer optionDimensions = ModelOptionValues.integerValue(request.options(), "dimensions").orElse(null);
            int dimension = LocalDeterministicRuntimeContract.requireConsistentEmbeddingDimensions(
                    request.embeddingDimension(),
                    optionDimensions
            );
            return EmbeddingOptions.builder()
                    .model(request.modelCode())
                    .dimensions(dimension)
                    .build();
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

    private static void validateRuntimeContract(
            SpringAiModelRuntimeRequest request,
            List<String> violations
    ) {
        try {
            LocalDeterministicRuntimeContract.validate(request);
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
        }
    }

    private static void validateAllowedOptions(
            Map<String, Object> options,
            String modelType,
            List<String> violations
    ) {
        Set<String> allowedOptions = "CHAT".equals(modelType)
                ? CHAT_OPTIONS
                : "EMBEDDING".equals(modelType) ? EMBEDDING_OPTIONS : Set.of();
        for (String key : options.keySet()) {
            if (!allowedOptions.contains(key)) {
                violations.add(modelType + " 不支持的本地模型 options 字段 " + key);
            }
        }
    }

    private static void validateChatOptions(Map<String, Object> options, List<String> violations) {
        ModelOptionValues.validateDoubleRange(options, "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(options, "topP", 0, 1, violations);
        ModelOptionValues.validateDoubleRange(options, "frequencyPenalty", -2, 2, violations);
        ModelOptionValues.validateDoubleRange(options, "presencePenalty", -2, 2, violations);
        ModelOptionValues.validateIntegerMin(options, "topK", 1, violations);
        ModelOptionValues.validateIntegerMin(options, "maxTokens", 1, violations);
        ModelOptionValues.validateStringList(options, "stopSequences", violations);
    }

    private static void validateEmbeddingDimensions(
            SpringAiModelRuntimeRequest request,
            List<String> violations
    ) {
        Optional<Integer> optionDimensions;
        try {
            optionDimensions = ModelOptionValues.integerValue(request.options(), "dimensions");
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
            return;
        }
        try {
            LocalDeterministicRuntimeContract.requireConsistentEmbeddingDimensions(
                    request.embeddingDimension(),
                    optionDimensions.orElse(null)
            );
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
        }
    }

    private static String normalizedProviderKind(SpringAiModelRuntimeRequest request) {
        return request.providerKind().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedModelType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
