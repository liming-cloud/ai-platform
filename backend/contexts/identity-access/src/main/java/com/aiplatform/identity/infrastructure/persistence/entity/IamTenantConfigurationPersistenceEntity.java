package com.aiplatform.identity.infrastructure.persistence.entity;

import com.aiplatform.identity.application.result.IamTenantResult;

public class IamTenantConfigurationPersistenceEntity {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String status;
    private String planCode;

    public IamTenantResult toResult() {
        return new IamTenantResult(String.valueOf(id), tenantCode, tenantName, status, planCode);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
}
