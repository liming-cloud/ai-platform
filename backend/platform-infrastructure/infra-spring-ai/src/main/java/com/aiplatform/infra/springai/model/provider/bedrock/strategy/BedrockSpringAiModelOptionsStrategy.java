package com.aiplatform.infra.springai.model.provider.bedrock.strategy;

import com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockProviderSupport;
import com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockSpringAiCredentialsFactory;
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
import java.util.function.Function;
import org.springframework.ai.bedrock.converse.BedrockChatOptions;
import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingOptions;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingOptions;
import org.springframework.ai.model.ModelOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Amazon Bedrock provider options 策略，使用 AWS 凭证材料和区域参数装配 Spring AI 原生模型。
 */
@Component
@Order(0)
public class BedrockSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of("CHAT", "REASONING", "EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "temperature",
            "topP",
            "topK",
            "maxTokens",
            "frequencyPenalty",
            "presencePenalty",
            "stopSequences",
            "region",
            "timeoutSeconds",
            "embeddingProvider",
            "inputType",
            "truncate"
    );

    @Override
    public String name() {
        return "bedrock-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return BedrockProviderSupport.supports(request) && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前 Amazon Bedrock 策略不支持 providerKind 或模型类型");
        }
        if (!BedrockProviderSupport.AUTH_TYPE_AWS_CREDENTIALS.equalsIgnoreCase(request.authType())) {
            violations.add("Amazon Bedrock provider 必须使用 aws_credentials 鉴权");
        }
        int violationCountBeforeRegion = violations.size();
        Optional<String> region = readStringOption(
                request.options(),
                BedrockProviderSupport.REGION_OPTION,
                violations
        );
        if (region.isEmpty() && violations.size() == violationCountBeforeRegion) {
            violations.add("Amazon Bedrock provider 必须配置 region");
        }
        if (region.isPresent()) {
            try {
                BedrockProviderSupport.requireCanonicalEndpointForRegion(request);
            } catch (IllegalArgumentException exception) {
                violations.add(exception.getMessage());
            }
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("Amazon Bedrock 不支持的模型 options 字段 " + key);
            }
        }
        if (!request.options().containsKey(BedrockProviderSupport.TIMEOUT_SECONDS_OPTION)) {
            violations.add("Amazon Bedrock timeoutSeconds 不能为空");
        }
        ModelOptionValues.validateDoubleRange(request.options(), "temperature", 0, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "topP", 0, 1, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "frequencyPenalty", -2, 2, violations);
        ModelOptionValues.validateDoubleRange(request.options(), "presencePenalty", -2, 2, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "topK", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "maxTokens", 1, violations);
        ModelOptionValues.validateIntegerMin(request.options(), "timeoutSeconds", 1, violations);
        ModelOptionValues.validateStringList(request.options(), "stopSequences", violations);
        validateEmbeddingOptions(request, violations);
        return violations.isEmpty()
                ? SpringAiModelValidationResult.passed()
                : SpringAiModelValidationResult.failed(violations);
    }

    @Override
    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Amazon Bedrock 模型 options 校验失败：" + validation.summary());
        }
        requireRuntimeCredentialMaterial(request);
        if ("EMBEDDING".equals(normalizedType(request))) {
            return buildEmbeddingOptions(request);
        }
        BedrockChatOptions.Builder builder = BedrockChatOptions.builder().model(request.modelCode());
        ModelOptionValues.doubleValue(request.options(), "temperature").ifPresent(builder::temperature);
        ModelOptionValues.doubleValue(request.options(), "topP").ifPresent(builder::topP);
        ModelOptionValues.integerValue(request.options(), "topK").ifPresent(builder::topK);
        ModelOptionValues.integerValue(request.options(), "maxTokens").ifPresent(builder::maxTokens);
        ModelOptionValues.doubleValue(request.options(), "frequencyPenalty").ifPresent(builder::frequencyPenalty);
        ModelOptionValues.doubleValue(request.options(), "presencePenalty").ifPresent(builder::presencePenalty);
        ModelOptionValues.stringListValue(request.options(), "stopSequences").ifPresent(builder::stopSequences);
        return builder.build();
    }

    private static ModelOptions buildEmbeddingOptions(SpringAiModelRuntimeRequest request) {
        if (BedrockProviderSupport.isCohereEmbedding(request)) {
            BedrockCohereEmbeddingOptions.Builder builder = BedrockCohereEmbeddingOptions.builder();
            ModelOptionValues.stringValue(request.options(), BedrockProviderSupport.INPUT_TYPE_OPTION)
                    .map(value -> enumValue(
                            CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType.class,
                            value,
                            "Amazon Bedrock Cohere inputType 不支持："
                    ))
                    .ifPresent(builder::inputType);
            ModelOptionValues.stringValue(request.options(), BedrockProviderSupport.TRUNCATE_OPTION)
                    .map(value -> enumValue(
                            CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate.class,
                            value,
                            "Amazon Bedrock Cohere truncate 不支持："
                    ))
                    .ifPresent(builder::truncate);
            return builder.build();
        }
        BedrockTitanEmbeddingOptions.Builder builder = BedrockTitanEmbeddingOptions.builder();
        ModelOptionValues.stringValue(request.options(), BedrockProviderSupport.INPUT_TYPE_OPTION)
                .map(value -> enumValue(
                        BedrockTitanEmbeddingModel.InputType.class,
                        value,
                        "Amazon Bedrock Titan inputType 不支持："
                ))
                .ifPresent(builder::inputType);
        return builder.build();
    }

    private static void requireRuntimeCredentialMaterial(SpringAiModelRuntimeRequest request) {
        BedrockSpringAiCredentialsFactory.create(request.credentialMaterial());
    }

    private static void validateEmbeddingOptions(SpringAiModelRuntimeRequest request, List<String> violations) {
        if (!"EMBEDDING".equals(normalizedType(request))) {
            return;
        }
        int violationCountBeforeEmbeddingProvider = violations.size();
        Optional<String> embeddingProviderOption = readStringOption(
                BedrockProviderSupport::embeddingProviderText,
                request,
                violations
        );
        String embeddingProvider = embeddingProviderOption.orElse(null);
        if (embeddingProvider == null && violations.size() == violationCountBeforeEmbeddingProvider) {
            violations.add("Amazon Bedrock embeddingProvider 不能为空");
            return;
        }
        if (embeddingProvider == null) {
            return;
        }
        if (!BedrockProviderSupport.EMBEDDING_PROVIDER_TITAN.equals(embeddingProvider)
                && !BedrockProviderSupport.EMBEDDING_PROVIDER_COHERE.equals(embeddingProvider)) {
            violations.add("Amazon Bedrock embeddingProvider 仅支持 titan 或 cohere");
        }
        readStringOption(request.options(), BedrockProviderSupport.INPUT_TYPE_OPTION, violations).ifPresent(value -> {
            try {
                if (BedrockProviderSupport.EMBEDDING_PROVIDER_COHERE.equals(embeddingProvider)) {
                    enumValue(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType.class, value, "");
                } else {
                    enumValue(BedrockTitanEmbeddingModel.InputType.class, value, "");
                }
            } catch (IllegalArgumentException exception) {
                String message = exception.getMessage();
                violations.add(message.isBlank() ? "Amazon Bedrock inputType 不支持：" + value : message);
            }
        });
        readStringOption(request.options(), BedrockProviderSupport.TRUNCATE_OPTION, violations).ifPresent(value -> {
            if (!BedrockProviderSupport.EMBEDDING_PROVIDER_COHERE.equals(embeddingProvider)) {
                violations.add("Amazon Bedrock truncate 仅适用于 Cohere embedding");
                return;
            }
            try {
                enumValue(CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate.class, value, "");
            } catch (IllegalArgumentException exception) {
                String message = exception.getMessage();
                violations.add(message.isBlank() ? "Amazon Bedrock Cohere truncate 不支持：" + value : message);
            }
        });
    }

    private static Optional<String> readStringOption(
            Function<SpringAiModelRuntimeRequest, Optional<String>> reader,
            SpringAiModelRuntimeRequest request,
            List<String> violations
    ) {
        try {
            return reader.apply(request);
        } catch (IllegalArgumentException exception) {
            violations.add(exception.getMessage());
            return Optional.empty();
        }
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

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String messagePrefix) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(messagePrefix + value);
        }
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
