package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 三层模型配置表的 MyBatis-Plus 元数据实体。
 */
@TableName("model_runtime.model_configuration")
public class ModelConfigurationEntity {

    @TableId(value = "id", type = IdType.INPUT) private Long id;
    private String scope;
    @TableField("tenant_id") private Long tenantId;
    @TableField("owner_membership_id") private Long ownerMembershipId;
    @TableField("display_name") private String displayName;
    @TableField("catalog_entry_id") private Long catalogEntryId;
    private String status;
    @Version private long version;
    @TableField("created_at") private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getOwnerMembershipId() { return ownerMembershipId; }
    public void setOwnerMembershipId(Long ownerMembershipId) { this.ownerMembershipId = ownerMembershipId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Long getCatalogEntryId() { return catalogEntryId; }
    public void setCatalogEntryId(Long catalogEntryId) { this.catalogEntryId = catalogEntryId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
