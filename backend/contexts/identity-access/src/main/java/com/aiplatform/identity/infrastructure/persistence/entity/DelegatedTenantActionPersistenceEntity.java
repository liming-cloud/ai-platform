package com.aiplatform.identity.infrastructure.persistence.entity;

import java.time.Instant;

/**
 * `iam_delegated_tenant_action` 持久化实体。
 */
public class DelegatedTenantActionPersistenceEntity {
    private Long id;
    private Long tenantId;
    private Long operatorId;
    private String actionType;
    private String targetResourceType;
    private String targetResourceId;
    private String reason;
    private String requestHash;
    private String auditEventId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTargetResourceType() { return targetResourceType; }
    public void setTargetResourceType(String targetResourceType) { this.targetResourceType = targetResourceType; }
    public String getTargetResourceId() { return targetResourceId; }
    public void setTargetResourceId(String targetResourceId) { this.targetResourceId = targetResourceId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getAuditEventId() { return auditEventId; }
    public void setAuditEventId(String auditEventId) { this.auditEventId = auditEventId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
