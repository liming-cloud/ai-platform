package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 模型供应商聚合根，统一管理供应商状态、鉴权方式和凭证引用。
 */
public record ModelProvider(
        TenantId tenantId,
        String providerId,
        String providerCode,
        String providerName,
        String endpoint,
        String authType,
        ModelSecretRef secretRef,
        ModelProviderStatus status,
        String approvalRequestId,
        String approvalEvidenceSchema,
        String approvalEvidenceDigest,
        String approvalTargetDigest,
        boolean approvalRequiredForConfigChange,
        Instant createdAt,
        Instant updatedAt,
        int version
) implements AggregateRoot<String> {
    public ModelProvider {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        providerId = requireText(providerId, "供应商 ID");
        providerCode = requireText(providerCode, "供应商编码");
        providerName = requireText(providerName, "供应商名称");
        endpoint = requireText(endpoint, "供应商访问地址");
        authType = ModelProviderAuthTypes.normalize(authType);
        Objects.requireNonNull(status, "供应商状态不能为空");
        approvalRequestId = normalizeOptionalText(approvalRequestId);
        if (status == ModelProviderStatus.PENDING_APPROVAL && approvalRequestId == null) {
            throw new IllegalArgumentException("待审批模型供应商必须绑定审批请求 ID");
        }
        String[] evidence = normalizeApprovalEvidence(
                approvalEvidenceSchema,
                approvalEvidenceDigest,
                approvalTargetDigest
        );
        approvalEvidenceSchema = evidence[0];
        approvalEvidenceDigest = evidence[1];
        approvalTargetDigest = evidence[2];
        if (ModelProviderAuthTypes.requiresCredentialMaterial(authType) && status == ModelProviderStatus.ACTIVE) {
            Objects.requireNonNull(secretRef, "ACTIVE 模型供应商凭证材料引用不能为空");
        }
        if (ModelProviderAuthTypes.isNoAuth(authType) && secretRef != null) {
            throw new IllegalArgumentException("no_auth 模型供应商不允许携带凭证材料引用");
        }
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("模型供应商版本不能小于 0");
        }
    }

    public ModelProvider(
            TenantId tenantId,
            String providerId,
            String providerCode,
            String providerName,
            String endpoint,
            String authType,
            ModelSecretRef secretRef,
            ModelProviderStatus status,
            String approvalRequestId,
            String approvalEvidenceSchema,
            String approvalEvidenceDigest,
            String approvalTargetDigest,
            boolean approvalRequiredForConfigChange,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(tenantId, providerId, providerCode, providerName, endpoint, authType, secretRef, status,
                approvalRequestId, approvalEvidenceSchema, approvalEvidenceDigest, approvalTargetDigest,
                approvalRequiredForConfigChange, createdAt, updatedAt, 0);
    }

    public ModelProvider(
            TenantId tenantId,
            String providerId,
            String providerCode,
            String providerName,
            String endpoint,
            String authType,
            ModelSecretRef secretRef,
            ModelProviderStatus status,
            String approvalRequestId,
            boolean approvalRequiredForConfigChange,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(tenantId, providerId, providerCode, providerName, endpoint, authType, secretRef, status,
                approvalRequestId, null, null, null, approvalRequiredForConfigChange, createdAt, updatedAt, 0);
    }

    public ModelProvider withApprovalEvidence(ApprovalEvidenceEnvelope evidence, String targetDigest) {
        Objects.requireNonNull(evidence, "模型供应商审批证据不能为空");
        if (status != ModelProviderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("只有待审批模型供应商允许绑定审批证据");
        }
        return new ModelProvider(
                tenantId, providerId, providerCode, providerName, endpoint, authType, secretRef, status,
                approvalRequestId, evidence.schema(), evidence.digest(), targetDigest,
                approvalRequiredForConfigChange, createdAt, updatedAt, version
        );
    }

    public boolean hasApprovalEvidence() {
        return approvalEvidenceSchema != null
                && approvalEvidenceDigest != null
                && approvalTargetDigest != null;
    }

    public ModelProvider approve(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        requirePendingApproval("批准");
        return withStatus(ModelProviderStatus.ACTIVE, updatedAt);
    }

    public ModelProvider reject(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        requirePendingApproval("拒绝");
        return withStatus(ModelProviderStatus.DISABLED, updatedAt);
    }

    private ModelProvider withStatus(ModelProviderStatus targetStatus, Instant updatedAt) {
        return new ModelProvider(
                tenantId,
                providerId,
                providerCode,
                providerName,
                endpoint,
                authType,
                secretRef,
                targetStatus,
                approvalRequestId,
                null,
                null,
                null,
                approvalRequiredForConfigChange,
                createdAt,
                updatedAt,
                version
        );
    }

    public ModelProvider withVersion(int persistedVersion) {
        return new ModelProvider(
                tenantId, providerId, providerCode, providerName, endpoint, authType, secretRef, status,
                approvalRequestId, approvalEvidenceSchema, approvalEvidenceDigest, approvalTargetDigest,
                approvalRequiredForConfigChange, createdAt, updatedAt, persistedVersion
        );
    }

    public ModelProvider disable(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        return withStatus(ModelProviderStatus.DISABLED, updatedAt);
    }

    @Override
    public String id() {
        return providerId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String[] normalizeApprovalEvidence(String schema, String evidenceDigest, String targetDigest) {
        boolean allMissing = isBlank(schema) && isBlank(evidenceDigest) && isBlank(targetDigest);
        if (allMissing) {
            return new String[]{null, null, null};
        }
        if (isBlank(schema) || isBlank(evidenceDigest) || isBlank(targetDigest)) {
            throw new IllegalArgumentException("模型供应商审批证据元数据必须同时存在");
        }
        String normalizedSchema = schema.trim();
        if (!ApprovalEvidenceEnvelope.MODEL_APPROVAL_SCHEMA_V1.equals(normalizedSchema)) {
            throw new IllegalArgumentException("模型供应商审批证据 schema 不受支持：" + normalizedSchema);
        }
        return new String[]{
                normalizedSchema,
                requireDigest(evidenceDigest, "模型供应商审批证据摘要"),
                requireDigest(targetDigest, "模型供应商审批目标摘要")
        };
    }

    private static String requireDigest(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(fieldName + "必须是 64 位 SHA-256 十六进制值");
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requirePendingApproval(String action) {
        if (status != ModelProviderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("只有待审批模型供应商可以" + action);
        }
    }
}
