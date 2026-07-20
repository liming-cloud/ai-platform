package com.aiplatform.modelhub.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.Objects;

/**
 * `mdl_provider` 表持久化实体，字段与数据库设计保持一一映射。
 */
@TableName("mdl_provider")
public class MdlProviderPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("provider_code")
    private String providerCode;

    @TableField("provider_name")
    private String providerName;

    @TableField("base_url")
    private String baseUrl;

    @TableField("auth_type")
    private String authType;

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

    @TableField("health_checked_at")
    private Instant healthCheckedAt;

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

    public ModelProvider toDomain(ModelSecretRef secretRef) {
        return new ModelProvider(
                TenantId.of(requireLong(tenantId, "模型供应商租户 ID")),
                requireLong(id, "模型供应商 ID"),
                providerCode,
                providerName,
                baseUrl,
                authType,
                secretRef,
                ModelProviderStatus.valueOf(status),
                approvalRequestId,
                approvalEvidenceSchema,
                approvalEvidenceDigest,
                approvalTargetDigest,
                true,
                createdAt,
                updatedAt,
                requireVersion(version, "模型供应商版本")
        );
    }

    public static MdlProviderPersistenceEntity fromDomain(ModelProvider provider) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        MdlProviderPersistenceEntity entity = new MdlProviderPersistenceEntity();
        entity.setId(parseRequiredLong(provider.providerId(), "模型供应商 ID"));
        entity.setTenantId(parseRequiredLong(provider.tenantId().value(), "租户 ID"));
        entity.setProviderCode(provider.providerCode());
        entity.setProviderName(provider.providerName());
        entity.setBaseUrl(provider.endpoint());
        entity.setAuthType(provider.authType());
        entity.setStatus(provider.status().name());
        entity.setApprovalRequestId(provider.approvalRequestId());
        entity.setApprovalEvidenceSchema(provider.approvalEvidenceSchema());
        entity.setApprovalEvidenceDigest(provider.approvalEvidenceDigest());
        entity.setApprovalTargetDigest(provider.approvalTargetDigest());
        entity.setCreatedAt(provider.createdAt());
        entity.setUpdatedAt(provider.updatedAt());
        entity.setVersion(provider.version());
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

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
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

    public Instant getHealthCheckedAt() {
        return healthCheckedAt;
    }

    public void setHealthCheckedAt(Instant healthCheckedAt) {
        this.healthCheckedAt = healthCheckedAt;
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
