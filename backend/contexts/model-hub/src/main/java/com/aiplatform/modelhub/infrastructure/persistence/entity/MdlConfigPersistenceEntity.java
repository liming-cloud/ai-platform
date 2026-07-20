package com.aiplatform.modelhub.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * `mdl_config` 表持久化实体，保存模型类型、价格、运行时 options 和连通性状态。
 */
@TableName("mdl_config")
public class MdlConfigPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_code")
    private String modelCode;

    @TableField("model_name")
    private String modelName;

    @TableField("model_type")
    private String modelType;

    @TableField("context_window")
    private Integer contextWindow;

    @TableField("embedding_dimension")
    private Integer embeddingDimension;

    @TableField("input_price_per_1k")
    private BigDecimal inputPricePer1k;

    @TableField("output_price_per_1k")
    private BigDecimal outputPricePer1k;

    @TableField("options_json")
    private String optionsJson;

    @TableField("connectivity_status")
    private String connectivityStatus;

    @TableField("connectivity_checked_at")
    private Instant connectivityCheckedAt;

    @TableField("connectivity_message")
    private String connectivityMessage;

    @TableField("status")
    private String status;

    @TableField("approval_request_id")
    private String approvalRequestId;

    @TableField("approval_evidence_schema")
    private String approvalEvidenceSchema;

    @TableField("approval_evidence_digest")
    private String approvalEvidenceDigest;

    @TableField("approval_target_digest")
    private String approvalTargetDigest;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("deleted_at")
    private Instant deletedAt;

    @TableField("version")
    private Integer version;

    public ModelConfig toDomain(ModelRuntimeOptions options) {
        return new ModelConfig(
                TenantId.of(requireLong(tenantId, "模型配置租户 ID")),
                requireLong(id, "模型配置 ID"),
                requireLong(providerId, "模型配置供应商 ID"),
                modelCode,
                modelName,
                ModelType.valueOf(modelType),
                contextWindow,
                embeddingDimension,
                inputPricePer1k,
                outputPricePer1k,
                ModelConfigStatus.valueOf(status),
                approvalRequestId,
                approvalEvidenceSchema,
                approvalEvidenceDigest,
                approvalTargetDigest,
                options,
                ModelConnectivityStatus.valueOf(connectivityStatus),
                connectivityCheckedAt,
                connectivityMessage,
                createdAt,
                updatedAt,
                requireVersion(version, "模型配置版本")
        );
    }

    public static MdlConfigPersistenceEntity fromDomain(ModelConfig config, String optionsJson) {
        Objects.requireNonNull(config, "模型配置不能为空");
        MdlConfigPersistenceEntity entity = new MdlConfigPersistenceEntity();
        entity.setId(parseRequiredLong(config.modelConfigId(), "模型配置 ID"));
        entity.setTenantId(parseRequiredLong(config.tenantId().value(), "租户 ID"));
        entity.setProviderId(parseRequiredLong(config.providerId(), "模型供应商 ID"));
        entity.setModelCode(config.modelCode());
        entity.setModelName(config.modelName());
        entity.setModelType(config.modelType().name());
        entity.setContextWindow(config.contextWindow());
        entity.setEmbeddingDimension(config.embeddingDimension());
        entity.setInputPricePer1k(config.inputPricePer1k());
        entity.setOutputPricePer1k(config.outputPricePer1k());
        entity.setOptionsJson(optionsJson);
        entity.setConnectivityStatus(config.connectivityStatus().name());
        entity.setConnectivityCheckedAt(config.connectivityCheckedAt());
        entity.setConnectivityMessage(config.connectivityMessage());
        entity.setStatus(config.status().name());
        entity.setApprovalRequestId(config.approvalRequestId());
        entity.setApprovalEvidenceSchema(config.approvalEvidenceSchema());
        entity.setApprovalEvidenceDigest(config.approvalEvidenceDigest());
        entity.setApprovalTargetDigest(config.approvalTargetDigest());
        entity.setCreatedAt(config.createdAt());
        entity.setUpdatedAt(config.updatedAt());
        entity.setVersion(config.version());
        return entity;
    }

    private static String requireLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return String.valueOf(value);
    }

    private static int requireVersion(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + "不能为空且不能小于 0");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public BigDecimal getInputPricePer1k() {
        return inputPricePer1k;
    }

    public void setInputPricePer1k(BigDecimal inputPricePer1k) {
        this.inputPricePer1k = inputPricePer1k;
    }

    public BigDecimal getOutputPricePer1k() {
        return outputPricePer1k;
    }

    public void setOutputPricePer1k(BigDecimal outputPricePer1k) {
        this.outputPricePer1k = outputPricePer1k;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public String getConnectivityStatus() {
        return connectivityStatus;
    }

    public void setConnectivityStatus(String connectivityStatus) {
        this.connectivityStatus = connectivityStatus;
    }

    public Instant getConnectivityCheckedAt() {
        return connectivityCheckedAt;
    }

    public void setConnectivityCheckedAt(Instant connectivityCheckedAt) {
        this.connectivityCheckedAt = connectivityCheckedAt;
    }

    public String getConnectivityMessage() {
        return connectivityMessage;
    }

    public void setConnectivityMessage(String connectivityMessage) {
        this.connectivityMessage = connectivityMessage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovalRequestId() {
        return approvalRequestId;
    }

    public void setApprovalRequestId(String approvalRequestId) {
        this.approvalRequestId = approvalRequestId;
    }

    public String getApprovalEvidenceSchema() {
        return approvalEvidenceSchema;
    }

    public void setApprovalEvidenceSchema(String approvalEvidenceSchema) {
        this.approvalEvidenceSchema = approvalEvidenceSchema;
    }

    public String getApprovalEvidenceDigest() {
        return approvalEvidenceDigest;
    }

    public void setApprovalEvidenceDigest(String approvalEvidenceDigest) {
        this.approvalEvidenceDigest = approvalEvidenceDigest;
    }

    public String getApprovalTargetDigest() {
        return approvalTargetDigest;
    }

    public void setApprovalTargetDigest(String approvalTargetDigest) {
        this.approvalTargetDigest = approvalTargetDigest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
