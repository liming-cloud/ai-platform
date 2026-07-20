package com.aiplatform.knowledge.infrastructure.persistence.converter;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbModelUsagePolicyProjectionPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 模型使用策略投影持久化转换器，集中处理枚举集合和 options JSON。
 */
@Component
public class KbModelUsagePolicyProjectionPersistenceConverter {
    private final JsonMapper objectMapper;

    public KbModelUsagePolicyProjectionPersistenceConverter(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public KnowledgeModelUsagePolicyProjection toDomain(KbModelUsagePolicyProjectionPersistenceEntity row) {
        Objects.requireNonNull(row, "模型策略投影行不能为空");
        return new KnowledgeModelUsagePolicyProjection(
                TenantId.of(requiredLongText(row.getTenantId(), "租户 ID")),
                requiredText(row.getModelConfigId(), "模型配置 ID"),
                requiredLongText(row.getProviderId(), "供应商 ID"),
                requiredText(row.getProviderCode(), "供应商编码"),
                requiredText(row.getProviderKind(), "供应商类型"),
                new KnowledgeModelRuntimeConnectionRef(
                        requiredText(row.getProviderEndpoint(), "供应商访问地址"),
                        requiredText(row.getAuthType(), "供应商鉴权方式"),
                        row.getSecretRefId(),
                        row.getSecretMaskedValue()
                ),
                requiredText(row.getModelCode(), "模型编码"),
                requiredInteger(row.getModelConfigVersion(), "模型配置版本"),
                requiredText(row.getModelType(), "模型类型"),
                requiredEnumSet(row.getCapabilities(), "模型能力", KnowledgeModelCapability.class),
                requiredEnumSet(row.getAllowedUseCases(), "允许使用场景", KnowledgeModelUseCase.class),
                textSet(row.getAllowedKnowledgeBaseIds(), "允许知识库范围"),
                readOptions(requiredText(row.getEffectiveOptionsJson(), "模型 options JSON")),
                requiredText(row.getEffectiveOptionsDigest(), "模型 effective options 摘要"),
                requiredInteger(row.getMaxInputTokens(), "输入 Token 上限"),
                requiredInteger(row.getMaxOutputTokens(), "输出 Token 上限"),
                requiredText(row.getPolicyVersion(), "策略版本"),
                requiredLong(row.getPolicyRevision(), "策略修订号"),
                requiredText(row.getPolicyDigest(), "策略摘要"),
                row.getExpiresAt(),
                requiredBoolean(row.getEnabled(), "策略启用状态"),
                requiredText(row.getSourceEventId(), "来源事件 ID"),
                requiredLong(row.getSourceEventVersion(), "来源事件版本"),
                requiredInstant(row.getLastEventAt(), "最后事件时间")
        );
    }

    public KbModelUsagePolicyProjectionPersistenceEntity toEntity(
            KnowledgeModelUsagePolicyProjection projection,
            Long existingId,
            Integer existingVersion
    ) {
        Objects.requireNonNull(projection, "模型策略投影不能为空");
        KbModelUsagePolicyProjectionPersistenceEntity entity = new KbModelUsagePolicyProjectionPersistenceEntity();
        entity.setId(existingId);
        entity.setTenantId(parseRequiredLong(projection.tenantId().value(), "租户 ID"));
        entity.setModelConfigId(projection.modelConfigId());
        entity.setProviderId(parseRequiredLong(projection.providerId(), "供应商 ID"));
        entity.setProviderCode(projection.providerCode());
        entity.setProviderKind(projection.providerKind());
        entity.setProviderEndpoint(projection.connectionRef().endpoint());
        entity.setAuthType(projection.connectionRef().authType());
        entity.setSecretRefId(projection.connectionRef().secretRefId());
        entity.setSecretMaskedValue(projection.connectionRef().secretMaskedValue());
        entity.setModelCode(projection.modelCode());
        entity.setModelConfigVersion(projection.modelConfigVersion());
        entity.setModelType(projection.modelType());
        entity.setCapabilities(joinEnums(projection.capabilities()));
        entity.setAllowedUseCases(joinEnums(projection.allowedUseCases()));
        entity.setAllowedKnowledgeBaseIds(String.join(",", projection.allowedKnowledgeBaseIds()));
        entity.setEffectiveOptionsJson(writeOptions(projection.effectiveOptions()));
        entity.setEffectiveOptionsDigest(projection.effectiveOptionsDigest());
        entity.setMaxInputTokens(projection.maxInputTokens());
        entity.setMaxOutputTokens(projection.maxOutputTokens());
        entity.setPolicyVersion(projection.policyVersion());
        entity.setPolicyRevision(projection.policyRevision());
        entity.setPolicyDigest(projection.policyDigest());
        entity.setEnabled(projection.enabled());
        entity.setExpiresAt(projection.expiresAt());
        entity.setSourceEventId(projection.sourceEventId());
        entity.setSourceEventVersion(projection.sourceEventVersion());
        entity.setLastEventAt(projection.lastEventAt());
        entity.setCreatedAt(projection.lastEventAt());
        entity.setUpdatedAt(projection.lastEventAt());
        entity.setVersion(entityVersion(existingId, existingVersion));
        return entity;
    }

    private Map<String, Object> readOptions(String optionsJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = objectMapper.readValue(optionsJson, Map.class);
            return values;
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型策略 options 反序列化失败", exception);
        }
    }

    private String writeOptions(Map<String, Object> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型策略 options 序列化失败", exception);
        }
    }

    private static <E extends Enum<E>> Set<E> requiredEnumSet(String value, String fieldName, Class<E> enumType) {
        Set<E> normalizedValues = textSet(value, fieldName).stream()
                .map(item -> Enum.valueOf(enumType, item))
                .collect(Collectors.toUnmodifiableSet());
        if (normalizedValues.isEmpty()) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return normalizedValues;
    }

    private static Set<String> textSet(String value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        if (value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",", -1))
                .map(item -> requireSetItem(item, fieldName))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireSetItem(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value.trim();
    }

    private static String joinEnums(Set<? extends Enum<?>> values) {
        return values.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
    }

    private static int entityVersion(Long existingId, Integer existingVersion) {
        if (existingId == null) {
            return 0;
        }
        return requiredInteger(existingVersion, "乐观锁版本");
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value.trim();
    }

    private static int requiredInteger(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static boolean requiredBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static long requiredLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value;
    }

    private static java.time.Instant requiredInstant(java.time.Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value;
    }
}
