package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 模型配置聚合根，描述同一供应商下可被平台选择的具体模型和运行时参数。
 */
public record ModelConfig(
        TenantId tenantId,
        String modelConfigId,
        String providerId,
        String modelCode,
        String modelName,
        ModelType modelType,
        Integer contextWindow,
        Integer embeddingDimension,
        BigDecimal inputPricePer1k,
        BigDecimal outputPricePer1k,
        ModelConfigStatus status,
        String approvalRequestId,
        String approvalEvidenceSchema,
        String approvalEvidenceDigest,
        String approvalTargetDigest,
        ModelRuntimeOptions options,
        ModelConnectivityStatus connectivityStatus,
        Instant connectivityCheckedAt,
        String connectivityMessage,
        Instant createdAt,
        Instant updatedAt,
        int version
) implements AggregateRoot<String> {
    public ModelConfig {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        providerId = requireText(providerId, "模型供应商 ID");
        modelCode = requireText(modelCode, "模型编码");
        modelName = requireText(modelName, "模型名称");
        Objects.requireNonNull(modelType, "模型类型不能为空");
        requirePositive(contextWindow, "上下文窗口");
        requirePositiveIfPresent(embeddingDimension, "向量维度");
        inputPricePer1k = ModelPriceContract.requireDatabaseValue(inputPricePer1k, "输入单价");
        outputPricePer1k = ModelPriceContract.requireDatabaseValue(outputPricePer1k, "输出单价");
        Objects.requireNonNull(status, "模型配置状态不能为空");
        approvalRequestId = normalizeOptionalText(approvalRequestId);
        if (status == ModelConfigStatus.PENDING_APPROVAL && approvalRequestId == null) {
            throw new IllegalArgumentException("待审批模型配置必须绑定审批请求 ID");
        }
        String[] evidence = normalizeApprovalEvidence(
                approvalEvidenceSchema,
                approvalEvidenceDigest,
                approvalTargetDigest
        );
        approvalEvidenceSchema = evidence[0];
        approvalEvidenceDigest = evidence[1];
        approvalTargetDigest = evidence[2];
        Objects.requireNonNull(options, "模型运行时 options 不能为空");
        Objects.requireNonNull(connectivityStatus, "模型连通性状态不能为空");
        connectivityMessage = normalizeConnectivity(
                connectivityStatus,
                connectivityCheckedAt,
                connectivityMessage
        );
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        requireVersion(version);
    }

    public ModelConfig(
            TenantId tenantId,
            String modelConfigId,
            String providerId,
            String modelCode,
            String modelName,
            ModelType modelType,
            Integer contextWindow,
            Integer embeddingDimension,
            BigDecimal inputPricePer1k,
            BigDecimal outputPricePer1k,
            ModelConfigStatus status,
            String approvalRequestId,
            String approvalEvidenceSchema,
            String approvalEvidenceDigest,
            String approvalTargetDigest,
            ModelRuntimeOptions options,
            ModelConnectivityStatus connectivityStatus,
            Instant connectivityCheckedAt,
            String connectivityMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(tenantId, modelConfigId, providerId, modelCode, modelName, modelType, contextWindow,
                embeddingDimension, inputPricePer1k, outputPricePer1k, status, approvalRequestId,
                approvalEvidenceSchema, approvalEvidenceDigest, approvalTargetDigest, options,
                connectivityStatus, connectivityCheckedAt, connectivityMessage, createdAt, updatedAt, 0);
    }

    public ModelConfig(
            TenantId tenantId,
            String modelConfigId,
            String providerId,
            String modelCode,
            String modelName,
            ModelType modelType,
            Integer contextWindow,
            Integer embeddingDimension,
            BigDecimal inputPricePer1k,
            BigDecimal outputPricePer1k,
            ModelConfigStatus status,
            String approvalRequestId,
            ModelRuntimeOptions options,
            ModelConnectivityStatus connectivityStatus,
            Instant connectivityCheckedAt,
            String connectivityMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(tenantId, modelConfigId, providerId, modelCode, modelName, modelType, contextWindow,
                embeddingDimension, inputPricePer1k, outputPricePer1k, status, approvalRequestId,
                null, null, null, options, connectivityStatus, connectivityCheckedAt,
                connectivityMessage, createdAt, updatedAt, 0);
    }

    public ModelConfig withApprovalEvidence(ApprovalEvidenceEnvelope evidence, String targetDigest) {
        Objects.requireNonNull(evidence, "模型配置审批证据不能为空");
        if (status != ModelConfigStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("只有待审批模型配置允许绑定审批证据");
        }
        return new ModelConfig(
                tenantId, modelConfigId, providerId, modelCode, modelName, modelType, contextWindow,
                embeddingDimension, inputPricePer1k, outputPricePer1k, status, approvalRequestId,
                evidence.schema(), evidence.digest(), targetDigest, options, connectivityStatus,
                connectivityCheckedAt, connectivityMessage, createdAt, updatedAt, version
        );
    }

    public boolean hasApprovalEvidence() {
        return approvalEvidenceSchema != null
                && approvalEvidenceDigest != null
                && approvalTargetDigest != null;
    }

    public ModelConfig withConnectivity(
            ModelRuntimeConnectivity connectivity,
            Instant checkedAt,
            Instant updatedAt
    ) {
        Objects.requireNonNull(connectivity, "模型连通性结果不能为空");
        Objects.requireNonNull(checkedAt, "连通性检查时间不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        return new ModelConfig(
                tenantId,
                modelConfigId,
                providerId,
                modelCode,
                modelName,
                modelType,
                contextWindow,
                embeddingDimension,
                inputPricePer1k,
                outputPricePer1k,
                status,
                approvalRequestId,
                approvalEvidenceSchema,
                approvalEvidenceDigest,
                approvalTargetDigest,
                options,
                connectivity.status(),
                checkedAt,
                connectivity.message(),
                createdAt,
                updatedAt,
                version
        );
    }

    public ModelConfig activate(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        return withStatus(ModelConfigStatus.ACTIVE, updatedAt);
    }

    public ModelConfig inactivate(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        return withStatus(ModelConfigStatus.INACTIVE, updatedAt);
    }

    public ModelConfig approve(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (status != ModelConfigStatus.PENDING_APPROVAL) {
            return this;
        }
        return withDecisionStatus(ModelConfigStatus.INACTIVE, updatedAt);
    }

    public ModelConfig reject(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (status != ModelConfigStatus.PENDING_APPROVAL) {
            return this;
        }
        return withDecisionStatus(ModelConfigStatus.REJECTED, updatedAt);
    }

    public ModelConfig deprecate(Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        return withStatus(ModelConfigStatus.DEPRECATED, updatedAt);
    }

    public ModelConfig withRuntimeSettings(
            String nextModelCode,
            String nextModelName,
            ModelType nextModelType,
            Integer nextContextWindow,
            Integer nextEmbeddingDimension,
            BigDecimal nextInputPricePer1k,
            BigDecimal nextOutputPricePer1k,
            ModelRuntimeOptions nextOptions,
            ModelConfigStatus nextStatus,
            String nextApprovalRequestId,
            Instant updatedAt
    ) {
        Objects.requireNonNull(nextModelType, "模型类型不能为空");
        Objects.requireNonNull(nextOptions, "模型运行时 options 不能为空");
        Objects.requireNonNull(nextStatus, "模型配置状态不能为空");
        Objects.requireNonNull(updatedAt, "更新时间不能为空");
        return new ModelConfig(
                tenantId,
                modelConfigId,
                providerId,
                nextModelCode,
                nextModelName,
                nextModelType,
                nextContextWindow,
                nextEmbeddingDimension,
                nextInputPricePer1k,
                nextOutputPricePer1k,
                nextStatus,
                nextApprovalRequestId,
                null,
                null,
                null,
                nextOptions,
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                createdAt,
                updatedAt,
                version
        );
    }

    @Override
    public String id() {
        return modelConfigId;
    }

    private ModelConfig withStatus(ModelConfigStatus nextStatus, Instant updatedAt) {
        return new ModelConfig(
                tenantId,
                modelConfigId,
                providerId,
                modelCode,
                modelName,
                modelType,
                contextWindow,
                embeddingDimension,
                inputPricePer1k,
                outputPricePer1k,
                nextStatus,
                approvalRequestId,
                null,
                null,
                null,
                options,
                connectivityStatus,
                connectivityCheckedAt,
                connectivityMessage,
                createdAt,
                updatedAt,
                version
        );
    }

    private ModelConfig withDecisionStatus(ModelConfigStatus nextStatus, Instant updatedAt) {
        return new ModelConfig(
                tenantId,
                modelConfigId,
                providerId,
                modelCode,
                modelName,
                modelType,
                contextWindow,
                embeddingDimension,
                inputPricePer1k,
                outputPricePer1k,
                nextStatus,
                approvalRequestId,
                null,
                null,
                null,
                options,
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                createdAt,
                updatedAt,
                version
        );
    }

    public ModelConfig withVersion(int persistedVersion) {
        return new ModelConfig(
                tenantId, modelConfigId, providerId, modelCode, modelName, modelType, contextWindow,
                embeddingDimension, inputPricePer1k, outputPricePer1k, status, approvalRequestId,
                approvalEvidenceSchema, approvalEvidenceDigest, approvalTargetDigest, options,
                connectivityStatus, connectivityCheckedAt, connectivityMessage, createdAt, updatedAt,
                persistedVersion
        );
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
            throw new IllegalArgumentException("模型配置审批证据元数据必须同时存在");
        }
        String normalizedSchema = schema.trim();
        if (!ApprovalEvidenceEnvelope.MODEL_APPROVAL_SCHEMA_V1.equals(normalizedSchema)) {
            throw new IllegalArgumentException("模型配置审批证据 schema 不受支持：" + normalizedSchema);
        }
        return new String[]{
                normalizedSchema,
                requireDigest(evidenceDigest, "模型配置审批证据摘要"),
                requireDigest(targetDigest, "模型配置审批目标摘要")
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

    private static String normalizeConnectivity(
            ModelConnectivityStatus status,
            Instant checkedAt,
            String message
    ) {
        if (status == ModelConnectivityStatus.NOT_TESTED) {
            if (checkedAt != null) {
                throw new IllegalArgumentException("未测试模型配置不得携带连通性检查时间");
            }
            if (message != null && !message.isBlank()) {
                throw new IllegalArgumentException("未测试模型配置不得携带连通性说明");
            }
            return "";
        }
        Objects.requireNonNull(checkedAt, "连通性检查时间不能为空");
        requireText(message, "模型连通性说明");
        return message.trim();
    }

    private static void requirePositiveIfPresent(Integer value, String fieldName) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

    private static void requireVersion(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("模型配置版本不能小于 0");
        }
    }

    private static void requirePositive(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

}
