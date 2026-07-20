package com.aiplatform.infra.springai.model.provider.openai;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible provider 的请求级 options，确保 Spring AI 2 收到 provider 专用类型。
 */
@Component
@Order(0)
public class OpenAiCompatibleSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "REASONING", "EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "temperature",
            "topP",
            "maxTokens",
            "frequencyPenalty",
            "presencePenalty",
            "stopSequences",
            "dimensions"
    );

    @Override
    public String name() {
        return "openai-compatible-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return OpenAiCompatibleSpringAiApiFactory.supports(request)
                && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前 OpenAI-compatible 策略不支持 providerKind 或模型类型");
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("OpenAI-compatible 不支持的模型 options 字段 " + key);
            }
        }
        ModelOptionValues.validateDoubleRange(request.options(), "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "topP", 0, 1, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "frequencyPenalty", -2, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "presencePenalty", -2, 2, violations);
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
            throw new IllegalArgumentException("OpenAI-compatible 模型 options 校验失败：" + validation.summary());
        }
        if ("EMBEDDING".equals(normalizedType(request))) {
            OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder()
                    .model(request.modelCode());
            ModelOptionValues.integerValue(request.options(), "dimensions").ifPresent(builder::dimensions);
            return builder.build();
        }
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(request.modelCode());
        ModelOptionValues.doubleValue(request.options(), "temperature").ifPresent(builder::temperature);
        ModelOptionValues.doubleValue(request.options(), "topP").ifPresent(builder::topP);
        ModelOptionValues.integerValue(request.options(), "maxTokens").ifPresent(builder::maxTokens);
        ModelOptionValues.doubleValue(request.options(), "frequencyPenalty").ifPresent(builder::frequencyPenalty);
        ModelOptionValues.doubleValue(request.options(), "presencePenalty").ifPresent(builder::presencePenalty);
        ModelOptionValues.stringListValue(request.options(), "stopSequences").ifPresent(builder::stop);
        return builder.build();
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
