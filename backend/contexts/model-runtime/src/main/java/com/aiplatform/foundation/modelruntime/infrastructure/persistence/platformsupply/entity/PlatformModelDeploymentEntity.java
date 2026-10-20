package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

/** 平台专表的最小普通 CRUD 实体；声明数组转换由独立 SQL 投影负责。 */
@com.baomidou.mybatisplus.annotation.TableName("model_runtime.platform_model_deployment")
public class PlatformModelDeploymentEntity {
    @com.baomidou.mybatisplus.annotation.TableId(value = "id", type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private Long id;
    @com.baomidou.mybatisplus.annotation.TableField("scope")
    private String scope;
    @com.baomidou.mybatisplus.annotation.TableField("status")
    private String status;
    @com.baomidou.mybatisplus.annotation.TableField("version")
    private Long version;
    @com.baomidou.mybatisplus.annotation.TableField("last_verification_id")
    private Long lastVerificationId;
    @com.baomidou.mybatisplus.annotation.TableField("verification_expires_at")
    private java.time.Instant verificationExpiresAt;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }

    public String getScope() { return scope; }
    public void setScope(String value) { this.scope = value; }

    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }

    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }

    public Long getLastVerificationId() { return lastVerificationId; }
    public void setLastVerificationId(Long value) { this.lastVerificationId = value; }

    public java.time.Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public void setVerificationExpiresAt(java.time.Instant value) { this.verificationExpiresAt = value; }
}
