package com.aiplatform.foundation.modelruntime.domain.model;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 固定供应商修订、模型类型和实际能力的模型部署。
 */
public final class ModelDeployment {

    private static final int MAX_CODE_CODE_POINTS = 200;
    private static final int MAX_NAME_CODE_POINTS = 80;

    private final TenantId tenantId;
    private final ModelDeploymentId id;
    private final ProviderConnectionId providerConnectionId;
    private final long providerConnectionRevision;
    private final String modelCode;
    private final String displayName;
    private final ModelType modelType;
    private final Set<ModelCapability> capabilities;
    private final int contextWindow;
    private final Integer outputLimit;
    private final Integer embeddingDimension;
    private final ModelDeploymentStatus status;
    private final long version;
    private final Instant createdAt;

    private ModelDeployment(
            TenantId tenantId,
            ModelDeploymentId id,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            String displayName,
            ModelType modelType,
            Set<ModelCapability> capabilities,
            int contextWindow,
            Integer outputLimit,
            Integer embeddingDimension,
            ModelDeploymentStatus status,
            long version,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        this.id = Objects.requireNonNull(id, "模型部署标识不能为空");
        this.providerConnectionId =
                Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        if (providerConnectionRevision < 0) {
            throw new IllegalArgumentException("供应商连接修订不能为负数");
        }
        this.providerConnectionRevision = providerConnectionRevision;
        this.modelCode = requireCode(modelCode);
        this.displayName = requireDisplayName(displayName);
        this.modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        this.capabilities = immutableCapabilities(capabilities);
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("上下文窗口必须为正数");
        }
        validateTypeConfiguration(modelType, this.capabilities, outputLimit, embeddingDimension);
        this.contextWindow = contextWindow;
        this.outputLimit = outputLimit;
        this.embeddingDimension = embeddingDimension;
        this.status = Objects.requireNonNull(status, "模型部署状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("模型部署版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static ModelDeployment create(
            TenantId tenantId,
            ModelDeploymentId id,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            String displayName,
            ModelType modelType,
            Set<ModelCapability> capabilities,
            int contextWindow,
            Integer outputLimit,
            Integer embeddingDimension,
            Instant createdAt) {
        return new ModelDeployment(
                tenantId,
                id,
                providerConnectionId,
                providerConnectionRevision,
                modelCode,
                displayName,
                modelType,
                capabilities,
                contextWindow,
                outputLimit,
                embeddingDimension,
                ModelDeploymentStatus.DRAFT,
                0,
                createdAt);
    }

    public static ModelDeployment reconstitute(
            TenantId tenantId,
            ModelDeploymentId id,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            String displayName,
            ModelType modelType,
            Set<ModelCapability> capabilities,
            int contextWindow,
            Integer outputLimit,
            Integer embeddingDimension,
            ModelDeploymentStatus status,
            long version,
            Instant createdAt) {
        return new ModelDeployment(
                tenantId,
                id,
                providerConnectionId,
                providerConnectionRevision,
                modelCode,
                displayName,
                modelType,
                capabilities,
                contextWindow,
                outputLimit,
                embeddingDimension,
                status,
                version,
                createdAt);
    }

    public ModelDeployment verify() {
        if (status != ModelDeploymentStatus.DRAFT) {
            throw new ModelDeploymentStateException("只有草稿模型部署可以验证");
        }
        return transitionTo(ModelDeploymentStatus.VERIFIED);
    }

    public ModelDeployment publish() {
        if (status != ModelDeploymentStatus.VERIFIED) {
            throw new ModelDeploymentStateException("只有已验证模型部署可以发布");
        }
        return transitionTo(ModelDeploymentStatus.PUBLISHED);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ModelDeploymentId id() {
        return id;
    }

    public ProviderConnectionId providerConnectionId() {
        return providerConnectionId;
    }

    public long providerConnectionRevision() {
        return providerConnectionRevision;
    }

    public String modelCode() {
        return modelCode;
    }

    public String displayName() {
        return displayName;
    }

    public ModelType modelType() {
        return modelType;
    }

    public Set<ModelCapability> capabilities() {
        return capabilities;
    }

    public int contextWindow() {
        return contextWindow;
    }

    public Integer outputLimit() {
        return outputLimit;
    }

    public Integer embeddingDimension() {
        return embeddingDimension;
    }

    public ModelDeploymentStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private ModelDeployment transitionTo(ModelDeploymentStatus nextStatus) {
        return new ModelDeployment(
                tenantId,
                id,
                providerConnectionId,
                providerConnectionRevision,
                modelCode,
                displayName,
                modelType,
                capabilities,
                contextWindow,
                outputLimit,
                embeddingDimension,
                nextStatus,
                version + 1,
                createdAt);
    }

    private static Set<ModelCapability> immutableCapabilities(
            Set<ModelCapability> capabilities) {
        Objects.requireNonNull(capabilities, "模型能力不能为空");
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("模型能力不能为空或包含空值");
        }
        for (var capability : capabilities) {
            if (capability == null) {
                throw new IllegalArgumentException("模型能力不能为空或包含空值");
            }
        }
        return Set.copyOf(capabilities);
    }

    private static void validateTypeConfiguration(
            ModelType modelType,
            Set<ModelCapability> capabilities,
            Integer outputLimit,
            Integer embeddingDimension) {
        if (modelType == ModelType.CHAT) {
            if (!capabilities.contains(ModelCapability.CHAT)) {
                throw new IllegalArgumentException("对话部署必须声明对话能力");
            }
            if (outputLimit == null || outputLimit <= 0) {
                throw new IllegalArgumentException("对话部署必须声明正数输出上限");
            }
            if (embeddingDimension != null) {
                throw new IllegalArgumentException("对话部署不得声明嵌入维度");
            }
            return;
        }
        if (!capabilities.contains(ModelCapability.EMBEDDING)) {
            throw new IllegalArgumentException("嵌入部署必须声明嵌入能力");
        }
        if (embeddingDimension == null || embeddingDimension <= 0) {
            throw new IllegalArgumentException("嵌入部署必须声明正数嵌入维度");
        }
        if (outputLimit != null) {
            throw new IllegalArgumentException("嵌入部署不得声明回答输出上限");
        }
    }

    private static String requireCode(String value) {
        Objects.requireNonNull(value, "模型代码不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_CODE_CODE_POINTS) {
            throw new IllegalArgumentException("模型代码格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))
                    || Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("模型代码不能包含空白或控制字符");
            }
        }
        return value;
    }

    private static String requireDisplayName(String value) {
        Objects.requireNonNull(value, "模型部署名称不能为空");
        if (value.isBlank()
                || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException("模型部署名称格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (Character.isISOControl(character)
                    || character == '\u2028'
                    || character == '\u2029') {
                throw new IllegalArgumentException("模型部署名称不能包含控制或换行字符");
            }
        }
        return value;
    }
}
