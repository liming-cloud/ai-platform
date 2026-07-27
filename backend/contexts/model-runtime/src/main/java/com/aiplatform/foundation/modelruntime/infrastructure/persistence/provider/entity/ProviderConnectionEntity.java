package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * MyBatis-Plus 表元数据实体，领域层不依赖该类型。
 */
@TableName("model_runtime.provider_connection")
public class ProviderConnectionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("provider_kind")
    private String providerKind;
    @TableField("display_name")
    private String displayName;
    @TableField("endpoint")
    private String endpoint;
    @TableField("region")
    private String region;
    @TableField("authentication_type")
    private String authenticationType;
    @TableField("credential_reference")
    private String credentialReference;
    @TableField("status")
    private String status;
    @Version
    @TableField("version")
    private long version;
    @TableField("last_probe_succeeded")
    private Boolean lastProbeSucceeded;
    @TableField("last_probe_code")
    private String lastProbeCode;
    @TableField("last_probed_at")
    private Instant lastProbedAt;
    @TableField("created_at")
    private Instant createdAt;

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

    public String getProviderKind() {
        return providerKind;
    }

    public void setProviderKind(String providerKind) {
        this.providerKind = providerKind;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getCredentialReference() {
        return credentialReference;
    }

    public void setCredentialReference(String credentialReference) {
        this.credentialReference = credentialReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Boolean getLastProbeSucceeded() {
        return lastProbeSucceeded;
    }

    public void setLastProbeSucceeded(Boolean lastProbeSucceeded) {
        this.lastProbeSucceeded = lastProbeSucceeded;
    }

    public String getLastProbeCode() {
        return lastProbeCode;
    }

    public void setLastProbeCode(String lastProbeCode) {
        this.lastProbeCode = lastProbeCode;
    }

    public Instant getLastProbedAt() {
        return lastProbedAt;
    }

    public void setLastProbedAt(Instant lastProbedAt) {
        this.lastProbedAt = lastProbedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
