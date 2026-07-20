package com.aiplatform.infra.springai.model.provider.ollama.strategy;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.strategy.support.ModelOptionValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ollama provider options 策略，避免把本地 no_auth provider 塞进 OpenAI-compatible 分支。
 */
@Component
@Order(0)
public class OllamaSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final String PROVIDER_KIND = "ollama";
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "REASONING", "EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "temperature",
            "topP",
            "topK",
            "maxTokens",
            "stopSequences",
            "dimensions",
            "keepAlive",
            "numCtx",
            "numBatch",
            "numThread",
            "numPredict"
    );

    @Override
    public String name() {
        return "ollama-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return supportsProviderKind(request) && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前 Ollama 策略不支持 providerKind 或模型类型");
        }
        if (!"no_auth".equalsIgnoreCase(request.authType())) {
            violations.add("Ollama provider 必须使用 no_auth 鉴权");
        }
        if (request.credentialMaterial() != null && !request.credentialMaterial().isBlank()) {
            violations.add("Ollama provider 不允许携带 API Key");
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("Ollama 不支持的模型 options 字段 " + key);
            }
        }
        ModelOptionValues.validateDoubleRange(request.options(), "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "topP", 0, 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "topK", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "maxTokens", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "numCtx", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "numBatch", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "numThread", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "numPredict", 1, violations);
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
            throw new IllegalArgumentException("Ollama 模型 options 校验失败：" + validation.summary());
        }
        if ("EMBEDDING".equals(normalizedType(request))) {
            OllamaEmbeddingOptions.Builder builder = OllamaEmbeddingOptions.builder()
                    .model(request.modelCode());
            ModelOptionValues.integerValue(request.options(), "dimensions")
                    .or(() -> Optional.ofNullable(request.embeddingDimension()))
                    .ifPresent(builder::dimensions);
            ModelOptionValues.integerValue(request.options(), "numBatch").ifPresent(builder::numBatch);
            ModelOptionValues.integerValue(request.options(), "numThread").ifPresent(builder::numThread);
            ModelOptionValues.stringValue(request.options(), "keepAlive").ifPresent(builder::keepAlive);
            return builder.build();
        }
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder().model(request.modelCode());
        ModelOptionValues.doubleValue(request.options(), "temperature").ifPresent(builder::temperature);
        ModelOptionValues.doubleValue(request.options(), "topP").ifPresent(builder::topP);
        ModelOptionValues.integerValue(request.options(), "topK").ifPresent(builder::topK);
        ModelOptionValues.integerValue(request.options(), "maxTokens").ifPresent(builder::numPredict);
        ModelOptionValues.integerValue(request.options(), "numPredict").ifPresent(builder::numPredict);
        ModelOptionValues.integerValue(request.options(), "numCtx").or(() -> Optional.ofNullable(request.contextWindow()))
                .ifPresent(builder::numCtx);
        ModelOptionValues.integerValue(request.options(), "numBatch").ifPresent(builder::numBatch);
        ModelOptionValues.integerValue(request.options(), "numThread").ifPresent(builder::numThread);
        ModelOptionValues.stringValue(request.options(), "keepAlive").ifPresent(builder::keepAlive);
        ModelOptionValues.stringListValue(request.options(), "stopSequences").ifPresent(builder::stop);
        return builder.build();
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }

    private static boolean supportsProviderKind(SpringAiModelRuntimeRequest request) {
        return PROVIDER_KIND.equals(request.providerKind().trim().toLowerCase(Locale.ROOT));
    }

}
