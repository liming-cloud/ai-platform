package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 模型配置授权事实表的 MyBatis-Plus 元数据实体。
 */
@TableName("model_runtime.model_configuration_access_grant")
public class ModelConfigurationAccessGrantEntity {

    @TableId(value = "id", type = IdType.INPUT) private Long id;
    @TableField("model_configuration_id") private Long modelConfigurationId;
    @TableField("grantee_tenant_id") private Long tenantId;
    @TableField("grantee_membership_id") private Long membershipId;
    private String status;
    @Version private long version;
    @TableField("created_at") private Instant createdAt;
    @TableField("revoked_at") private Instant revokedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getModelConfigurationId() { return modelConfigurationId; }
    public void setModelConfigurationId(Long modelConfigurationId) { this.modelConfigurationId = modelConfigurationId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}
