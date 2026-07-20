package com.aiplatform.modelhub.infrastructure.messaging.publisher;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.springai.model.provider.ProviderErrorSanitizer;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderAuthTypes;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.infrastructure.messaging.model.ModelUsagePolicyPublishedPayload;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelUsagePolicyOutboxMapper;
import com.aiplatform.modelhub.infrastructure.springai.adapter.ModelRuntimeAuthorityDigest;
import com.aiplatform.shared.kernel.Sha256Digests;
import com.aiplatform.shared.observability.TraceContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型使用策略发布适配器：只在当前数据库事务中写 outbox，不直接触发消息网关。
 */
@Component
public class RocketmqModelUsagePolicyPublisher implements ModelUsagePolicyPublisher {
    private static final String EVENT_TYPE = "ModelUsagePolicyPublished";
    private static final String AGGREGATE_TYPE = "ModelConfig";
    private static final String PRODUCER = "model-hub";
    private static final long EVENT_VERSION = 2;
    private static final String SCHEMA_VERSION = "schema/model-usage-policy.v2";
    private final ModelUsagePolicyOutboxMapper outbox;
    private final JsonMapper objectMapper;
    private final ModelRuntimeAuthorityDigest authorityDigest;

    public RocketmqModelUsagePolicyPublisher(
            ModelUsagePolicyOutboxMapper outbox,
            JsonMapper objectMapper,
            ModelRuntimeAuthorityDigest authorityDigest
    ) {
        this.outbox = Objects.requireNonNull(outbox, "模型策略 outbox Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
        this.authorityDigest = Objects.requireNonNull(
                authorityDigest,
                "模型运行时 authority 摘要服务不能为空"
        );
    }

    @Override
    @Transactional
    public void publish(ModelProvider provider, ModelConfig config) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(config, "模型配置不能为空");
        long policyRevision = requiredPolicyRevision(outbox.nextPolicyRevision());
        Instant occurredAt = config.updatedAt();
        DomainEventMessage message = new DomainEventMessage(
                EVENT_TYPE + ":" + UUID.randomUUID(),
                EVENT_TYPE,
                config.tenantId().value(),
                AGGREGATE_TYPE,
                config.modelConfigId(),
                EVENT_VERSION,
                SCHEMA_VERSION,
                occurredAt,
                TraceContext.currentTraceId(),
                PRODUCER,
                payloadJson(provider, config, policyRevision)
        );
        if (outbox.enqueue(message) != 1) {
            throw new IllegalStateException("模型使用策略 outbox 写入失败");
        }
    }

    private String payloadJson(ModelProvider provider, ModelConfig config, long policyRevision) {
        try {
            Map<String, Object> effectiveOptions = effectiveOptions(config);
            String effectiveOptionsDigest = authorityDigest.digest(effectiveOptions);
            ModelUsagePolicyPublishedPayload unsignedPayload = payload(
                    provider,
                    config,
                    policyRevision,
                    "",
                    effectiveOptions,
                    effectiveOptionsDigest
            );
            String policyDigest = Sha256Digests.requireLowercase(
                    outbox.canonicalDigest(objectMapper.writeValueAsString(unsignedPayload)),
                    "模型使用策略摘要"
            );
            return objectMapper.writeValueAsString(payload(
                    provider,
                    config,
                    policyRevision,
                    policyDigest,
                    effectiveOptions,
                    effectiveOptionsDigest
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型使用策略事件载荷序列化失败", exception);
        }
    }

    private ModelUsagePolicyPublishedPayload payload(
            ModelProvider provider,
            ModelConfig config,
            long policyRevision,
            String policyDigest,
            Map<String, Object> effectiveOptions,
            String effectiveOptionsDigest
    ) {
        return new ModelUsagePolicyPublishedPayload(
                    config.modelConfigId(),
                    provider.providerId(),
                    provider.providerCode(),
                    providerKind(config),
                    secretSafeEndpoint(provider.endpoint()),
                    normalizedAuthType(provider),
                    secretRefId(provider),
                    secretMaskedValue(provider),
                    config.modelCode(),
                    config.version(),
                    config.modelType().name(),
                    capabilities(config.modelType()),
                    allowedUseCases(config.modelType()),
                    List.of(),
                    List.of(),
                    effectiveOptions,
                    effectiveOptionsDigest,
                    config.contextWindow(),
                    0,
                    policyVersion(config, policyRevision),
                    policyRevision,
                    policyDigest,
                    null,
                    enabled(provider, config)
            );
    }

    private static String providerKind(ModelConfig config) {
        return config.options().providerKind();
    }

    private static Map<String, Object> effectiveOptions(ModelConfig config) {
        Map<String, Object> options = Map.copyOf(config.options().springAiOptions());
        rejectSecretOptions(options);
        return options;
    }

    private static void rejectSecretOptions(Map<?, ?> options) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        rejectSecretOptions(options, visited);
    }

    private static void rejectSecretOptions(Map<?, ?> options, Set<Object> visited) {
        if (!visited.add(options)) {
            return;
        }
        for (Map.Entry<?, ?> entry : options.entrySet()) {
            String key = String.valueOf(entry.getKey());
            rejectSecretText(key, true);
            rejectNestedSecretOptions(entry.getValue(), visited);
        }
    }

    private static void rejectNestedSecretOptions(Object value, Set<Object> visited) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            rejectSecretText(text, false);
            return;
        }
        if (value instanceof Map<?, ?> nested) {
            rejectSecretOptions(nested, visited);
            return;
        }
        if (value instanceof Collection<?> values) {
            if (!visited.add(values)) {
                return;
            }
            for (Object element : values) {
                rejectNestedSecretOptions(element, visited);
            }
            return;
        }
        if (value.getClass().isArray()) {
            if (!visited.add(value)) {
                return;
            }
            for (int index = 0; index < Array.getLength(value); index++) {
                rejectNestedSecretOptions(Array.get(value, index), visited);
            }
        }
    }

    private static void rejectSecretText(String value, boolean key) {
        if ((key && isSecretOptionKey(value)) || ProviderErrorSanitizer.containsSensitiveMaterial(value)) {
            throw new IllegalArgumentException("模型 options 包含禁止进入事件的秘密字段或材料");
        }
    }

    private static String normalizedOptionKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean isSecretOptionKey(String key) {
        String normalized = normalizedOptionKey(key);
        return normalized.contains("apikey")
                || normalized.contains("accesskey")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("credential")
                || normalized.contains("privatekey")
                || normalized.contains("signature")
                || normalized.equals("sig")
                || normalized.endsWith("token");
    }

    private static String secretSafeEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型供应商访问地址格式非法");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("模型供应商访问地址不能携带认证材料");
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null) {
            for (String parameter : rawQuery.split("&")) {
                String rawKey = parameter.split("=", 2)[0];
                if (isSecretOptionKey(decodeQueryKey(rawKey))) {
                    throw new IllegalArgumentException("模型供应商访问地址不能携带认证材料");
                }
            }
        }
        return endpoint;
    }

    private static String decodeQueryKey(String rawKey) {
        String decoded = rawKey;
        try {
            for (int depth = 0; depth < 2; depth++) {
                String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (next.equals(decoded)) {
                    break;
                }
                decoded = next;
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型供应商访问地址格式非法");
        }
    }

    private static List<String> capabilities(ModelType modelType) {
        return switch (modelType) {
            case EMBEDDING -> List.of("EMBEDDING");
            case RERANK -> List.of("RERANK");
            case CHAT, REASONING -> List.of("CHAT");
            case IMAGE_GENERATION, IMAGE_UNDERSTANDING, SPEECH_TO_TEXT, TEXT_TO_SPEECH -> List.of();
        };
    }

    private static List<String> allowedUseCases(ModelType modelType) {
        return switch (modelType) {
            case EMBEDDING -> List.of("RAG_INDEXING", "RAG_QUERY");
            case CHAT, REASONING -> List.of("RAG_QUERY");
            case RERANK, IMAGE_GENERATION, IMAGE_UNDERSTANDING, SPEECH_TO_TEXT, TEXT_TO_SPEECH -> List.of();
        };
    }

    private static boolean enabled(ModelProvider provider, ModelConfig config) {
        return provider.status() == ModelProviderStatus.ACTIVE
                && config.status() == ModelConfigStatus.ACTIVE
                && config.connectivityStatus() == ModelConnectivityStatus.PASSED
                && authReady(provider);
    }

    private static boolean authReady(ModelProvider provider) {
        String authType = normalizedAuthType(provider);
        if (ModelProviderAuthTypes.requiresCredentialMaterial(authType)) {
            return configuredSecret(provider);
        }
        return ModelProviderAuthTypes.isNoAuth(authType);
    }

    private static String secretRefId(ModelProvider provider) {
        return ModelProviderAuthTypes.requiresCredentialMaterial(normalizedAuthType(provider))
                ? secretId(provider)
                : null;
    }

    private static String secretMaskedValue(ModelProvider provider) {
        return ModelProviderAuthTypes.requiresCredentialMaterial(normalizedAuthType(provider))
                ? secretMaskedValueOrNull(provider)
                : null;
    }

    private static boolean configuredSecret(ModelProvider provider) {
        String secretId = secretId(provider);
        return secretId != null && !secretId.isBlank();
    }

    private static String secretId(ModelProvider provider) {
        return provider.secretRef() == null ? null : provider.secretRef().secretId();
    }

    private static String secretMaskedValueOrNull(ModelProvider provider) {
        return provider.secretRef() == null ? null : provider.secretRef().maskedValue();
    }

    private static String normalizedAuthType(ModelProvider provider) {
        return ModelProviderAuthTypes.normalize(provider.authType());
    }

    private static String policyVersion(ModelConfig config, long policyRevision) {
        return "mdl-%s-r%s".formatted(config.modelConfigId(), policyRevision);
    }

    private static long requiredPolicyRevision(Long revision) {
        if (revision == null || revision <= 0 || revision == Long.MAX_VALUE) {
            throw new IllegalStateException("模型使用策略修订号生成失败");
        }
        return revision;
    }

}
