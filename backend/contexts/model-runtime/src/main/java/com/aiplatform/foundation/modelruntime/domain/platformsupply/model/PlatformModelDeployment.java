package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 平台拥有的可调用模型部署。
 *
 * <p>该聚合不携带租户标识；目录只能引用已发布且验证尚未到期的修订，避免任一租户改变平台共享供应。</p>
 */
public final class PlatformModelDeployment implements AggregateRoot<PlatformModelDeploymentId> {

    private static final int MAX_CODE_CODE_POINTS = 200;
    private static final int MAX_NAME_CODE_POINTS = 80;

    private final PlatformModelSupplyScope scope;
    private final PlatformModelDeploymentId id;
    private final PlatformProviderConnectionId providerConnectionId;
    private final long providerConnectionRevision;
    private final String modelCode;
    private final String displayName;
    private final ModelType modelType;
    private final Set<ModelCapability> capabilities;
    private final int contextWindow;
    private final Integer outputLimit;
    private final Integer embeddingDimension;
    private final PlatformModelCapabilityVerificationId lastVerificationId;
    private final Instant verificationExpiresAt;
    private final PlatformModelDeploymentStatus status;
    private final long version;
    private final Instant createdAt;
    private final PlatformModelDeploymentInputs inputs;

    private PlatformModelDeployment(
            PlatformModelDeploymentId id,
            PlatformProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            String displayName,
            ModelType modelType,
            Set<ModelCapability> capabilities,
            int contextWindow,
            Integer outputLimit,
            Integer embeddingDimension,
            PlatformModelCapabilityVerificationId lastVerificationId,
            Instant verificationExpiresAt,
            PlatformModelDeploymentStatus status,
            long version,
            Instant createdAt, PlatformModelDeploymentInputs inputs) {
        this.scope = PlatformModelSupplyScope.PLATFORM;
        this.id = Objects.requireNonNull(id, "平台模型部署标识不能为空");
        this.providerConnectionId = Objects.requireNonNull(providerConnectionId, "平台供应商连接标识不能为空");
        if (providerConnectionRevision < 0) {
            throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        }
        this.providerConnectionRevision = providerConnectionRevision;
        this.modelCode = requireCode(modelCode);
        this.displayName = requireDisplayName(displayName);
        this.modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        this.capabilities = immutableCapabilities(capabilities);
        if (contextWindow <= 0) {
            throw new IllegalArgumentException("上下文窗口必须为正数");
        }
        validateTypeConfiguration(modelType, this.capabilities, outputLimit, embeddingDimension, inputs);
        if (outputLimit != null && outputLimit > contextWindow) throw new IllegalArgumentException("输出上限不能超过上下文窗口");
        this.inputs = inputs;
        this.contextWindow = contextWindow;
        this.outputLimit = outputLimit;
        this.embeddingDimension = embeddingDimension;
        status = Objects.requireNonNull(status, "平台模型部署状态不能为空");
        if (status == PlatformModelDeploymentStatus.DRAFT
                && (lastVerificationId != null || verificationExpiresAt != null)) {
            throw new IllegalArgumentException("草稿平台模型部署不得携带成功验证事实");
        }
        if ((status == PlatformModelDeploymentStatus.VERIFIED
                || status == PlatformModelDeploymentStatus.PUBLISHED)
                && (lastVerificationId == null || verificationExpiresAt == null)) {
            throw new IllegalArgumentException("已验证或已发布平台模型部署必须携带成功验证事实");
        }
        this.lastVerificationId = lastVerificationId;
        this.verificationExpiresAt = verificationExpiresAt;
        this.status = status;
        if (version < 0) {
            throw new IllegalArgumentException("平台模型部署版本不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    public static PlatformModelDeployment create(
            PlatformModelDeploymentId id,
            PlatformProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            String displayName,
            ModelType modelType,
            Set<ModelCapability> capabilities,
            int contextWindow,
            Integer outputLimit,
            Integer embeddingDimension,
            Instant createdAt) {
        return new PlatformModelDeployment(
                id, providerConnectionId, providerConnectionRevision, modelCode, displayName, modelType,
                capabilities, contextWindow, outputLimit, embeddingDimension, null, null,
                PlatformModelDeploymentStatus.DRAFT, 0, createdAt, null);
    }

    public static PlatformModelDeployment reconstitute(
            PlatformModelDeploymentId id,
            PlatformProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            String displayName,
            ModelType modelType,
            Set<ModelCapability> capabilities,
            int contextWindow,
            Integer outputLimit,
            Integer embeddingDimension,
            PlatformModelCapabilityVerificationId lastVerificationId,
            Instant verificationExpiresAt,
            PlatformModelDeploymentStatus status,
            long version,
            Instant createdAt) {
        return new PlatformModelDeployment(
                id, providerConnectionId, providerConnectionRevision, modelCode, displayName, modelType,
                capabilities, contextWindow, outputLimit, embeddingDimension, lastVerificationId,
                verificationExpiresAt, status, version, createdAt, null);
    }

    /**
     * 固化成功能力验证；只有草稿部署可以进入已验证状态。
     *
     * @throws PlatformModelDeploymentStateException 部署已不是草稿。
     */
    public PlatformModelDeployment verify(
            PlatformModelCapabilityVerificationId verificationId,
            Instant expiresAt) {
        if (status != PlatformModelDeploymentStatus.DRAFT) {
            throw new PlatformModelDeploymentStateException("只有草稿平台模型部署可以验证");
        }
        return transitionTo(
                PlatformModelDeploymentStatus.VERIFIED,
                Objects.requireNonNull(verificationId, "平台模型能力验证标识不能为空"),
                Objects.requireNonNull(expiresAt, "验证失效时间不能为空"));
    }

    /**
     * 发布前再次检查验证未到期，目录不得从过期部署创建。
     *
     * @throws PlatformModelDeploymentStateException 部署非已验证状态或验证已过期。
     */
    public PlatformModelDeployment publish(Instant now) {
        if (status != PlatformModelDeploymentStatus.VERIFIED) {
            throw new PlatformModelDeploymentStateException("只有已验证平台模型部署可以发布");
        }
        now = Objects.requireNonNull(now, "当前时间不能为空");
        if (!verificationExpiresAt.isAfter(now)) {
            throw new PlatformModelDeploymentStateException("过期验证不能发布平台模型部署");
        }
        return transitionTo(PlatformModelDeploymentStatus.PUBLISHED, lastVerificationId, verificationExpiresAt);
    }

    /** 停用保留验证审计事实，且不能在同一修订恢复。 */
    public PlatformModelDeployment disable() {
        if (status == PlatformModelDeploymentStatus.DISABLED) {
            return this;
        }
        return transitionTo(PlatformModelDeploymentStatus.DISABLED, lastVerificationId, verificationExpiresAt);
    }

    public PlatformModelSupplyScope scope() { return scope; }
    @Override public PlatformModelDeploymentId id() { return id; }
    public PlatformProviderConnectionId providerConnectionId() { return providerConnectionId; }
    public long providerConnectionRevision() { return providerConnectionRevision; }
    public String modelCode() { return modelCode; }
    public String displayName() { return displayName; }
    public ModelType modelType() { return modelType; }
    public Set<ModelCapability> capabilities() { return capabilities; }
    public int contextWindow() { return contextWindow; }
    public Integer outputLimit() { return outputLimit; }
    public Integer embeddingDimension() { return embeddingDimension; }
    public PlatformModelCapabilityVerificationId lastVerificationId() { return lastVerificationId; }
    public Instant verificationExpiresAt() { return verificationExpiresAt; }
    public PlatformModelDeploymentStatus status() { return status; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    /** 历史部署未声明的输入返回空值，不能推测参数来源或拟授权集合。 */
    public PlatformModelDeploymentInputs inputs() { return inputs; }

    /** 创建完整声明草稿；声明不等于真实能力或生效授权。 */
    public static PlatformModelDeployment createDraft(PlatformModelDeploymentId id, PlatformProviderConnectionId connectionId,
            long revision, String modelCode, String displayName, ModelType type, Set<ModelCapability> capabilities,
            int contextWindow, Integer outputLimit, Integer dimension, Instant createdAt, PlatformModelDeploymentInputs inputs) {
        return new PlatformModelDeployment(id, connectionId, revision, modelCode, displayName, type, capabilities,
                contextWindow, outputLimit, dimension, null, null, PlatformModelDeploymentStatus.DRAFT, 0, createdAt,
                Objects.requireNonNull(inputs, "完整草稿必须声明输入"));
    }

    /** 从持久化快照恢复完整声明，不改变验证或发布事实。 */
    public static PlatformModelDeployment restore(PlatformModelDeploymentId id, PlatformProviderConnectionId connectionId,
            long revision, String modelCode, String displayName, ModelType type, Set<ModelCapability> capabilities,
            int contextWindow, Integer outputLimit, Integer dimension, PlatformModelCapabilityVerificationId verificationId,
            Instant expiresAt, PlatformModelDeploymentStatus status, long version, Instant createdAt, PlatformModelDeploymentInputs inputs) {
        return new PlatformModelDeployment(id, connectionId, revision, modelCode, displayName, type, capabilities,
                contextWindow, outputLimit, dimension, verificationId, expiresAt, status, version, createdAt, inputs);
    }

    private PlatformModelDeployment transitionTo(
            PlatformModelDeploymentStatus nextStatus,
            PlatformModelCapabilityVerificationId verificationId,
            Instant expiresAt) {
        return new PlatformModelDeployment(
                id, providerConnectionId, providerConnectionRevision, modelCode, displayName, modelType,
                capabilities, contextWindow, outputLimit, embeddingDimension, verificationId, expiresAt,
                nextStatus, version + 1, createdAt, inputs);
    }

    private static Set<ModelCapability> immutableCapabilities(Set<ModelCapability> values) {
        values = Objects.requireNonNull(values, "模型能力不能为空");
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("模型能力不能为空或包含空值");
        }
        return Set.copyOf(values);
    }

    private static void validateTypeConfiguration(ModelType modelType, Set<ModelCapability> capabilities,
            Integer outputLimit, Integer embeddingDimension, PlatformModelDeploymentInputs inputs) {
        var required = ModelCapability.valueOf(modelType.name());
        if (!capabilities.equals(Set.of(required))) throw new IllegalArgumentException("部署只能声明当前模型类型的能力");
        var rerank = inputs == null ? null : inputs.rerankCandidateLimit();
        switch (modelType) {
            case CHAT -> {
                if (outputLimit == null || outputLimit <= 0 || embeddingDimension != null || rerank != null)
                    throw new IllegalArgumentException("对话部署必须声明正数输出上限，不能混入其他类型参数");
            }
            case EMBEDDING -> {
                if (embeddingDimension == null || embeddingDimension <= 0 || outputLimit != null || rerank != null)
                    throw new IllegalArgumentException("向量部署必须声明正数维度，不能混入其他类型参数");
            }
            case RERANK -> {
                if (rerank == null || outputLimit != null || embeddingDimension != null)
                    throw new IllegalArgumentException("重排部署必须声明正数候选上限，不能混入其他类型参数");
            }
        }
    }

    private static String requireCode(String value) {
        value = Objects.requireNonNull(value, "模型代码不能为空");
        if (value.isBlank() || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_CODE_CODE_POINTS
                || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("模型代码格式不正确");
        }
        return value;
    }

    private static String requireDisplayName(String value) {
        value = Objects.requireNonNull(value, "平台模型部署名称不能为空");
        if (value.codePointCount(0, value.length()) < 2 || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > MAX_NAME_CODE_POINTS
                || value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u2028' || c == '\u2029')) {
            throw new IllegalArgumentException("平台模型部署名称格式不正确");
        }
        return value;
    }
}
