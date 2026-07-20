package com.aiplatform.knowledge.infrastructure.messaging.converter;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.knowledge.infrastructure.messaging.model.ModelUsagePolicyPublishedPayload;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 模型使用策略事件转换器，隔离消息协议和 knowledge-base 领域投影。
 */
@Component("knowledgeModelUsagePolicyPublishedMessageConverter")
public class ModelUsagePolicyPublishedMessageConverter {
    private final JsonMapper objectMapper;

    public ModelUsagePolicyPublishedMessageConverter(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public KnowledgeModelUsagePolicyProjection toProjection(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        ModelUsagePolicyPublishedPayload payload = payload(message);
        return new KnowledgeModelUsagePolicyProjection(
                TenantId.of(message.tenantId()),
                requireText(payload.modelConfigId(), "模型配置 ID"),
                requireText(payload.providerId(), "供应商 ID"),
                requireText(payload.providerCode(), "供应商编码"),
                requireText(payload.providerKind(), "供应商类型"),
                new KnowledgeModelRuntimeConnectionRef(
                        payload.providerEndpoint(),
                        payload.authType(),
                        payload.secretRefId(),
                        payload.secretMaskedValue()
                ),
                requireText(payload.modelCode(), "模型编码"),
                requiredInteger(payload.modelConfigVersion(), "模型配置版本"),
                requireText(payload.modelType(), "模型类型"),
                requiredEnumSet(payload.capabilities(), "模型能力", KnowledgeModelCapability.class),
                requiredEnumSet(payload.allowedUseCases(), "允许使用场景", KnowledgeModelUseCase.class),
                textSet(payload.allowedKnowledgeBaseIds(), "允许知识库范围"),
                requiredMap(payload.effectiveOptions(), "模型 options"),
                requireText(payload.effectiveOptionsDigest(), "模型 effective options 摘要"),
                requiredInteger(payload.maxInputTokens(), "输入 Token 上限"),
                requiredInteger(payload.maxOutputTokens(), "输出 Token 上限"),
                requireText(payload.policyVersion(), "策略版本"),
                requiredLong(payload.policyRevision(), "策略修订号"),
                requireText(payload.policyDigest(), "策略摘要"),
                payload.expiresAt(),
                requiredBoolean(payload.enabled(), "策略启用状态"),
                message.messageId(),
                message.version(),
                message.occurredAt()
        );
    }

    private ModelUsagePolicyPublishedPayload payload(DomainEventMessage message) {
        try {
            return objectMapper.readValue(message.payloadJson(), ModelUsagePolicyPublishedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("模型使用策略事件载荷解析失败", exception);
        }
    }

    private static <E extends Enum<E>> Set<E> requiredEnumSet(List<String> values, String fieldName, Class<E> enumType) {
        Set<E> normalizedValues = textSet(values, fieldName).stream()
                .map(value -> Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
        if (normalizedValues.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalizedValues;
    }

    private static Set<String> textSet(List<String> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream()
                .map(value -> requireText(value, fieldName))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static Map<String, Object> requiredMap(Map<String, Object> value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static int requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static long requiredLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static boolean requiredBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
