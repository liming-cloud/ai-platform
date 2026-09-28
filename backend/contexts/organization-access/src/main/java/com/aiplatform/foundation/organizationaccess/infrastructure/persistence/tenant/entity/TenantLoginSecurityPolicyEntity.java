package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Objects;

/** MyBatis-Plus 单表载体；恢复时由领域对象重新核对策略范围。 */
@TableName("organization_access.tenant_login_security_policy")
public class TenantLoginSecurityPolicyEntity {
    @TableId(type = IdType.INPUT)
    private Long tenantId;
    private Integer sessionHours;
    private Integer failedAttempts;
    private Integer lockMinutes;
    private Long version;

    public static TenantLoginSecurityPolicyEntity from(TenantLoginSecurityPolicy policy) {
        Objects.requireNonNull(policy, "登录策略不能为空");
        var row = new TenantLoginSecurityPolicyEntity();
        row.tenantId = policy.tenantId().value();
        row.sessionHours = policy.sessionHours();
        row.failedAttempts = policy.failedAttempts();
        row.lockMinutes = policy.lockMinutes();
        row.version = policy.version();
        return row;
    }

    public TenantLoginSecurityPolicy toDomain() {
        return new TenantLoginSecurityPolicy(new TenantId(tenantId), sessionHours, failedAttempts, lockMinutes, version);
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Integer getSessionHours() { return sessionHours; }
    public void setSessionHours(Integer value) { sessionHours = value; }
    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer value) { failedAttempts = value; }
    public Integer getLockMinutes() { return lockMinutes; }
    public void setLockMinutes(Integer value) { lockMinutes = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
}
