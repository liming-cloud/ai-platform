package com.aiplatform.modelhub.infrastructure.persistence.entity;

import java.time.Instant;

/** Persistence row for a tenant-scoped ModelHub create request claim. */
public class ModelCreationRequestPersistenceEntity {
    private Long id;
    private Long tenantId;
    private String resourceType;
    private String requestId;
    private String requestDigest;
    private Long resultParentResourceId;
    private Long resultResourceId;
    private String resultResourceCode;
    private String resultModelType;
    private String resultStatus;
    private String resultApprovalRequestId;
    private String resultConnectivityStatus;
    private Instant createdAt;
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRequestDigest() { return requestDigest; }
    public void setRequestDigest(String requestDigest) { this.requestDigest = requestDigest; }
    public Long getResultParentResourceId() { return resultParentResourceId; }
    public void setResultParentResourceId(Long resultParentResourceId) { this.resultParentResourceId = resultParentResourceId; }
    public Long getResultResourceId() { return resultResourceId; }
    public void setResultResourceId(Long resultResourceId) { this.resultResourceId = resultResourceId; }
    public String getResultResourceCode() { return resultResourceCode; }
    public void setResultResourceCode(String resultResourceCode) { this.resultResourceCode = resultResourceCode; }
    public String getResultModelType() { return resultModelType; }
    public void setResultModelType(String resultModelType) { this.resultModelType = resultModelType; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public String getResultApprovalRequestId() { return resultApprovalRequestId; }
    public void setResultApprovalRequestId(String resultApprovalRequestId) { this.resultApprovalRequestId = resultApprovalRequestId; }
    public String getResultConnectivityStatus() { return resultConnectivityStatus; }
    public void setResultConnectivityStatus(String resultConnectivityStatus) { this.resultConnectivityStatus = resultConnectivityStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
