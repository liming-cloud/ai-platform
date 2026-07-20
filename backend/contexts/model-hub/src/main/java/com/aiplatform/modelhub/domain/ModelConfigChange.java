package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.DomainEntity;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

/**
 * 模型审批变更事实，统一承载模型供应商与模型配置的审批发布语义。
 */
public record ModelConfigChange(
        TenantId tenantId,
        String changeId,
        String providerId,
        String modelConfigId,
        String approvalType,
        String sourceResourceType,
        String sourceResourceId,
        String changeSummary,
        ModelConfigRiskLevel riskLevel,
        String operatorId,
        String traceId,
        Instant requestedAt,
        ApprovalEvidenceEnvelope approvalEvidence,
        String targetDigest
) implements DomainEntity<String> {
    public static final String APPROVAL_TYPE_MODEL_PROVIDER_CHANGE = "model_provider_change";
    public static final String APPROVAL_TYPE_MODEL_CONFIG_CHANGE = "model_config_change";
    public static final String RESOURCE_TYPE_MODEL_PROVIDER = "model_provider";
    public static final String RESOURCE_TYPE_MODEL_CONFIG = "model_config";
    private static final String ACTION_MODEL_PROVIDER_CREATE = "MODEL_PROVIDER_CREATE";
    private static final String ACTION_MODEL_CONFIG_CREATE = "MODEL_CONFIG_CREATE";

    public ModelConfigChange {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        changeId = requireText(changeId, "配置变更ID");
        providerId = requireText(providerId, "模型供应商ID");
        modelConfigId = normalizeOptionalText(modelConfigId);
        approvalType = requireText(approvalType, "审批类型");
        sourceResourceType = requireText(sourceResourceType, "来源资源类型");
        sourceResourceId = requireText(sourceResourceId, "来源资源ID");
        changeSummary = requireText(changeSummary, "配置变更摘要");
        Objects.requireNonNull(riskLevel, "配置变更风险等级不能为空");
        operatorId = requireText(operatorId, "操作人ID");
        traceId = requireText(traceId, "链路追踪ID");
        requestedAt = canonicalRequestedAt(requestedAt);
        approvalEvidence = Objects.requireNonNull(approvalEvidence, "模型审批证据不能为空");
        targetDigest = requireText(targetDigest, "模型审批目标摘要");
        validateEvidence(
                approvalEvidence,
                targetDigest,
                tenantId,
                changeId,
                approvalType,
                sourceResourceType,
                sourceResourceId,
                providerId,
                modelConfigId,
                operatorId,
                requestedAt,
                riskLevel
        );
    }

    public static ModelConfigChange forProviderCreation(
            ModelProvider provider,
            boolean credentialConfigured,
            String operatorId,
            String traceId,
            Instant requestedAt
    ) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        requestedAt = canonicalRequestedAt(requestedAt);
        ModelApprovalEvidence evidence = ModelApprovalEvidence.forProviderCreation(
                provider,
                credentialConfigured,
                operatorId,
                requestedAt
        );
        return new ModelConfigChange(
                provider.tenantId(),
                provider.approvalRequestId(),
                provider.providerId(),
                null,
                APPROVAL_TYPE_MODEL_PROVIDER_CHANGE,
                RESOURCE_TYPE_MODEL_PROVIDER,
                provider.providerId(),
                "创建模型供应商 " + provider.providerName(),
                ModelConfigRiskLevel.HIGH_RISK,
                operatorId,
                traceId,
                requestedAt,
                evidence.envelope(),
                evidence.targetDigest()
        );
    }

    public static ModelConfigChange forModelConfigCreation(
            ModelProvider provider,
            ModelConfig target,
            String operatorId,
            String traceId,
            Instant requestedAt
    ) {
        Objects.requireNonNull(target, "模型配置不能为空");
        requestedAt = canonicalRequestedAt(requestedAt);
        ModelApprovalEvidence evidence = ModelApprovalEvidence.forModelConfigCreation(
                provider,
                target,
                operatorId,
                requestedAt
        );
        return new ModelConfigChange(
                target.tenantId(),
                target.approvalRequestId(),
                target.providerId(),
                target.modelConfigId(),
                APPROVAL_TYPE_MODEL_CONFIG_CHANGE,
                RESOURCE_TYPE_MODEL_CONFIG,
                target.modelConfigId(),
                "创建模型配置 " + target.modelName(),
                ModelConfigRiskLevel.HIGH_RISK,
                operatorId,
                traceId,
                requestedAt,
                evidence.envelope(),
                evidence.targetDigest()
        );
    }

    public static ModelConfigChange forModelConfigUpdate(
            ModelProvider provider,
            ModelConfig before,
            ModelConfig target,
            String operatorId,
            String traceId,
            Instant requestedAt
    ) {
        Objects.requireNonNull(target, "模型配置不能为空");
        requestedAt = canonicalRequestedAt(requestedAt);
        ModelApprovalEvidence evidence = ModelApprovalEvidence.forModelConfigUpdate(
                provider,
                before,
                target,
                operatorId,
                requestedAt
        );
        return new ModelConfigChange(
                target.tenantId(),
                target.approvalRequestId(),
                target.providerId(),
                target.modelConfigId(),
                APPROVAL_TYPE_MODEL_CONFIG_CHANGE,
                RESOURCE_TYPE_MODEL_CONFIG,
                target.modelConfigId(),
                "更新模型配置 " + target.modelName(),
                ModelConfigRiskLevel.HIGH_RISK,
                operatorId,
                traceId,
                requestedAt,
                evidence.envelope(),
                evidence.targetDigest()
        );
    }

    public boolean requiresApproval() {
        return riskLevel == ModelConfigRiskLevel.HIGH_RISK;
    }

    public String auditResourceType() {
        return switch (sourceResourceType) {
            case RESOURCE_TYPE_MODEL_PROVIDER -> RESOURCE_TYPE_MODEL_PROVIDER.toUpperCase();
            case RESOURCE_TYPE_MODEL_CONFIG -> RESOURCE_TYPE_MODEL_CONFIG.toUpperCase();
            default -> throw new IllegalStateException("未知来源资源类型：" + sourceResourceType);
        };
    }

    public String auditAction() {
        Object action = approvalEvidence.facts().get("action");
        if (action instanceof String value && !value.isBlank()) {
            return value;
        }
        return switch (sourceResourceType) {
            case RESOURCE_TYPE_MODEL_PROVIDER -> ACTION_MODEL_PROVIDER_CREATE;
            case RESOURCE_TYPE_MODEL_CONFIG -> ACTION_MODEL_CONFIG_CREATE;
            default -> throw new IllegalStateException("未知来源资源类型：" + sourceResourceType);
        };
    }

    @Override
    public String id() {
        return changeId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static Instant canonicalRequestedAt(Instant value) {
        return Objects.requireNonNull(value, "配置变更请求时间不能为空").truncatedTo(ChronoUnit.MICROS);
    }

    private static void validateEvidence(
            ApprovalEvidenceEnvelope evidence,
            String targetDigest,
            TenantId tenantId,
            String requestId,
            String approvalType,
            String resourceType,
            String resourceId,
            String providerId,
            String modelConfigId,
            String operatorId,
            Instant requestedAt,
            ModelConfigRiskLevel riskLevel
    ) {
        Map<String, Object> facts = evidence.facts();
        requireFact(facts, "tenantId", tenantId.value());
        requireFact(facts, "approvalRequestId", requestId);
        requireFact(facts, "approvalType", approvalType);
        requireFact(facts, "sourceContext", "model-hub");
        requireFact(facts, "resourceType", resourceType);
        requireFact(facts, "resourceId", resourceId);
        requireFact(facts, "providerId", providerId);
        requireFact(facts, "modelConfigId", modelConfigId);
        requireFact(facts, "requesterId", operatorId);
        requireFact(facts, "requestedAt", requestedAt.toString());
        requireFact(facts, "riskLevel", riskLevel.name());
        Object target = facts.get("target");
        if (!(target instanceof Map<?, ?> targetFacts)) {
            throw new IllegalArgumentException("模型审批证据 target 必须是对象");
        }
        String expectedTarget = ApprovalEvidenceEnvelope.digestFacts(Map.of("target", targetFacts));
        if (!java.security.MessageDigest.isEqual(
                expectedTarget.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                targetDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException("模型审批目标摘要与证据 target 不一致");
        }
    }

    private static void requireFact(Map<String, Object> facts, String name, Object expected) {
        if (!Objects.equals(facts.get(name), expected)) {
            throw new IllegalArgumentException("模型审批证据字段不一致：" + name);
        }
    }
}
