package com.aiplatform.appstudio.infrastructure.persistence.converter;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppModelUsagePolicyProjectionPersistenceEntity;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * AI 应用模型策略投影持久化转换器，集中处理集合字段和 options JSON。
 */
@Component
public class AppModelUsagePolicyProjectionPersistenceConverter {
    private final JsonMapper objectMapper;

    public AppModelUsagePolicyProjectionPersistenceConverter(JsonMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public AppModelUsagePolicyProjection toDomain(AppModelUsagePolicyProjectionPersistenceEntity row) {
        Objects.requireNonNull(row, "模型策略投影行不能为空");
        return new AppModelUsagePolicyProjection(
                TenantId.of(requiredLongText(row.getTenantId(), "租户 ID")),
                requiredLongText(row.getModelConfigId(), "模型配置 ID"),
                requiredLongText(row.getProviderId(), "供应商 ID"),
                requiredText(row.getProviderCode(), "供应商编码"),
                requiredText(row.getProviderKind(), "供应商类型"),
                new AppModelRuntimeConnectionRef(
                        requiredText(row.getProviderEndpoint(), "供应商访问地址"),
                        requiredText(row.getAuthType(), "供应商鉴权方式"),
                        row.getSecretRefId()
                ),
                requiredText(row.getModelCode(), "模型编码"),
                requiredInteger(row.getModelConfigVersion(), "模型配置版本"),
                requiredText(row.getModelType(), "模型类型"),
                requiredSet(row.getCapabilities(), "模型能力"),
                requiredSet(row.getAllowedUseCases(), "允许使用场景"),
                optionalSet(row.getAllowedApplicationIds()),
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

    public AppModelUsagePolicyProjectionPersistenceEntity toEntity(
            AppModelUsagePolicyProjection projection,
            Long existingId,
            Integer existingVersion
    ) {
        Objects.requireNonNull(projection, "模型策略投影不能为空");
        AppModelUsagePolicyProjectionPersistenceEntity entity = new AppModelUsagePolicyProjectionPersistenceEntity();
        entity.setId(existingId);
        entity.setTenantId(parseRequiredLong(projection.tenantId().value(), "租户 ID"));
        entity.setModelConfigId(parseRequiredLong(projection.modelConfigId(), "模型配置 ID"));
        entity.setProviderId(parseRequiredLong(projection.providerId(), "供应商 ID"));
        entity.setProviderCode(projection.providerCode());
        entity.setProviderKind(projection.providerKind());
        entity.setProviderEndpoint(projection.connectionRef().endpoint());
        entity.setAuthType(projection.connectionRef().authType());
        entity.setSecretRefId(projection.connectionRef().secretRefId());
        entity.setModelCode(projection.modelCode());
        entity.setModelConfigVersion(projection.modelConfigVersion());
        entity.setModelType(projection.modelType());
        entity.setCapabilities(joinSet(projection.capabilities()));
        entity.setAllowedUseCases(joinSet(projection.allowedUseCases()));
        entity.setAllowedApplicationIds(joinOptionalSet(projection.allowedApplicationIds()));
        entity.setEffectiveOptionsJson(writeOptions(projection.effectiveOptions()));
        entity.setEffectiveOptionsDigest(projection.effectiveOptionsDigest());
        entity.setMaxInputTokens(projection.maxInputTokens());
        entity.setMaxOutputTokens(projection.maxOutputTokens());
        entity.setPolicyVersion(projection.policyVersion());
        entity.setPolicyRevision(projection.policyRevision());
        entity.setPolicyDigest(projection.policyDigest());
        entity.setExpiresAt(projection.expiresAt());
        entity.setEnabled(projection.enabled());
        entity.setSourceEventId(projection.sourceEventId());
        entity.setSourceEventVersion(projection.sourceEventVersion());
        entity.setLastEventAt(projection.lastEventAt());
        entity.setCreatedAt(projection.lastEventAt());
        entity.setUpdatedAt(projection.lastEventAt());
        entity.setVersion(existingId == null ? 0 : requiredInteger(existingVersion, "乐观锁版本"));
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

    private static Set<String> requiredSet(String value, String fieldName) {
        Set<String> values = optionalSet(value);
        if (values.isEmpty()) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return values;
    }

    private static Set<String> optionalSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",", -1))
                .map(item -> requiredText(item, "集合值"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String joinSet(Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining(","));
    }

    private static String joinOptionalSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return joinSet(values);
    }

    private static String requiredLongText(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return String.valueOf(value);
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

    private static Instant requiredInstant(Instant value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("模型策略投影持久化记录缺少" + fieldName);
        }
        return value;
    }
}
