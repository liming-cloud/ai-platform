package com.aiplatform.appstudio.infrastructure.messaging.converter;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.infrastructure.messaging.model.ModelUsagePolicyPublishedPayload;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 模型使用策略事件转换器，隔离消息协议和 app-studio 领域投影。
 */
@Component("appStudioModelUsagePolicyPublishedMessageConverter")
public class ModelUsagePolicyPublishedMessageConverter {
    private final JsonMapper objectMapper;

    public ModelUsagePolicyPublishedMessageConverter(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public AppModelUsagePolicyProjection toProjection(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        ModelUsagePolicyPublishedPayload payload = payload(message);
        return new AppModelUsagePolicyProjection(
                TenantId.of(message.tenantId()),
                requireText(payload.modelConfigId(), "模型配置 ID"),
                requireText(payload.providerId(), "供应商 ID"),
                requireText(payload.providerCode(), "供应商编码"),
                requireText(payload.providerKind(), "供应商类型"),
                new AppModelRuntimeConnectionRef(
                        requireText(payload.providerEndpoint(), "供应商访问地址"),
                        requireText(payload.authType(), "供应商鉴权方式"),
                        payload.secretRefId()
                ),
                requireText(payload.modelCode(), "模型编码"),
                requiredInteger(payload.modelConfigVersion(), "模型配置版本"),
                requireText(payload.modelType(), "模型类型"),
                requiredUppercaseSet(payload.capabilities(), "模型能力"),
                requiredUppercaseSet(payload.allowedUseCases(), "允许使用场景"),
                textSet(payload.allowedApplicationIds(), "允许应用范围"),
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

    private static Set<String> requiredUppercaseSet(List<String> values, String fieldName) {
        Set<String> normalizedValues = textSet(values, fieldName).stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedValues.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return Set.copyOf(normalizedValues);
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
