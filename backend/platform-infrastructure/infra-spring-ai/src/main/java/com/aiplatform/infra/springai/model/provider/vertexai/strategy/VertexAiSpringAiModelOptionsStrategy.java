package com.aiplatform.infra.springai.model.provider.vertexai.strategy;

import com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiProviderSupport;
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
import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Vertex AI provider options 策略，使用 GCP service account 凭证和 project/location 装配 Spring AI。
 */
@Component
@Order(0)
public class VertexAiSpringAiModelOptionsStrategy implements SpringAiModelOptionsStrategy {
    private static final Set<String> SUPPORTED_TYPES = Set.of("EMBEDDING");
    private static final Set<String> ALLOWED_OPTIONS = Set.of(
            "projectId",
            "location",
            "publisher",
            "dimensions",
            "taskType",
            "title",
            "autoTruncate"
    );

    @Override
    public String name() {
        return "vertex-ai-spring-ai-options";
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return VertexAiProviderSupport.supports(request) && SUPPORTED_TYPES.contains(normalizedType(request));
    }

    @Override
    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        List<String> violations = new ArrayList<>();
        if (!supports(request)) {
            violations.add("当前 Vertex AI 策略不支持 providerKind 或模型类型");
        }
        if (!VertexAiProviderSupport.AUTH_TYPE_GCP_SERVICE_ACCOUNT.equalsIgnoreCase(request.authType())) {
            violations.add("Vertex AI provider 必须使用 gcp_service_account 鉴权");
        }
        int violationCountBeforeProject = violations.size();
        Optional<String> projectId = readStringOption(
                request.options(),
                VertexAiProviderSupport.PROJECT_ID_OPTION,
                violations
        );
        if (projectId.isEmpty() && violations.size() == violationCountBeforeProject) {
            violations.add("Vertex AI provider 必须配置 projectId");
        }
        int violationCountBeforeLocation = violations.size();
        Optional<String> location = readStringOption(
                request.options(),
                VertexAiProviderSupport.LOCATION_OPTION,
                violations
        );
        if (location.isEmpty() && violations.size() == violationCountBeforeLocation) {
            violations.add("Vertex AI provider 必须配置 location");
        }
        for (String key : request.options().keySet()) {
            if (!ALLOWED_OPTIONS.contains(key)) {
                violations.add("Vertex AI 不支持的模型 options 字段 " + key);
            }
        }
        if (location.isPresent() && !request.options().containsKey(VertexAiProviderSupport.API_ENDPOINT_OPTION)) {
            try {
                VertexAiProviderSupport.apiEndpoint(request);
            } catch (IllegalArgumentException exception) {
                violations.add(exception.getMessage());
            }
        }
        validateSharedOptions(request, violations);
        validateEmbeddingOptions(request, violations);
        return violations.isEmpty()
                ? SpringAiModelValidationResult.passed()
                : SpringAiModelValidationResult.failed(violations);
    }

    @Override
    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Vertex AI 模型 options 校验失败：" + validation.summary());
        }
        VertexAiProviderSupport.apiEndpoint(request);
        requireRuntimeCredentialMaterial(request);
        VertexAiTextEmbeddingOptions.Builder builder = VertexAiTextEmbeddingOptions.builder()
                .model(request.modelCode());
        ModelOptionValues.integerValue(request.options(), "dimensions")
                .or(() -> java.util.Optional.ofNullable(request.embeddingDimension()))
                .ifPresent(builder::dimensions);
        ModelOptionValues.stringValue(request.options(), VertexAiProviderSupport.TASK_TYPE_OPTION)
                .map(VertexAiSpringAiModelOptionsStrategy::taskType)
                .ifPresent(builder::taskType);
        ModelOptionValues.stringValue(request.options(), VertexAiProviderSupport.TITLE_OPTION).ifPresent(builder::title);
        ModelOptionValues.booleanValue(request.options(), VertexAiProviderSupport.AUTO_TRUNCATE_OPTION).ifPresent(builder::autoTruncate);
        return builder.build();
    }

    private static void requireRuntimeCredentialMaterial(SpringAiModelRuntimeRequest request) {
        com.aiplatform.infra.springai.model.provider.vertexai.support.VertexAiSpringAiCredentialFactory
                .create(request.credentialMaterial());
    }

    private static void validateSharedOptions(SpringAiModelRuntimeRequest request, List<String> violations) {
        ModelOptionValues.validateIntegerMin(request.options(), "dimensions", 1, violations);
        ModelOptionValues.validateStringOption(request.options(), "projectId", violations);
        ModelOptionValues.validateStringOption(request.options(), "location", violations);
        ModelOptionValues.validateStringOption(request.options(), "publisher", violations);
        ModelOptionValues.validateStringOption(request.options(), "title", violations);
        ModelOptionValues.validateBooleanOption(request.options(), "autoTruncate", violations);
    }

    private static void validateEmbeddingOptions(SpringAiModelRuntimeRequest request, List<String> violations) {
        if (!"EMBEDDING".equals(normalizedType(request))) {
            return;
        }
        if (request.embeddingDimension() == null && !request.options().containsKey("dimensions")) {
            violations.add("Vertex AI 向量模型必须配置 embeddingDimension 或 dimensions");
        }
        ModelOptionValues.validateRequiredStringOption(
                request.options(),
                VertexAiProviderSupport.PUBLISHER_OPTION,
                "Vertex AI publisher",
                violations
        );
        readStringOption(request.options(), VertexAiProviderSupport.TASK_TYPE_OPTION, violations).ifPresent(value -> {
            try {
                taskType(value);
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

    private static VertexAiTextEmbeddingOptions.TaskType taskType(String value) {
        try {
            return VertexAiTextEmbeddingOptions.TaskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Vertex AI taskType 不支持：" + value);
        }
    }

    private static String normalizedType(SpringAiModelRuntimeRequest request) {
        return request.modelType().trim().toUpperCase(Locale.ROOT);
    }
}
