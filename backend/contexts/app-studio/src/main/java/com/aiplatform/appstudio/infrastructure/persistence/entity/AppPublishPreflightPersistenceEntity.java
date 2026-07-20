package com.aiplatform.appstudio.infrastructure.persistence.entity;

import java.time.Instant;

public class AppPublishPreflightPersistenceEntity {
    private Long id;
    private Long tenantId;
    private Long applicationId;
    private String publishRequestId;
    private String candidateFingerprint;
    private Boolean passed;
    private String blockersJson;
    private String warningsJson;
    private String traceId;
    private Long checkedBy;
    private Instant checkedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getPublishRequestId() { return publishRequestId; }
    public void setPublishRequestId(String publishRequestId) { this.publishRequestId = publishRequestId; }
    public String getCandidateFingerprint() { return candidateFingerprint; }
    public void setCandidateFingerprint(String candidateFingerprint) { this.candidateFingerprint = candidateFingerprint; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public String getBlockersJson() { return blockersJson; }
    public void setBlockersJson(String blockersJson) { this.blockersJson = blockersJson; }
    public String getWarningsJson() { return warningsJson; }
    public void setWarningsJson(String warningsJson) { this.warningsJson = warningsJson; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getCheckedBy() { return checkedBy; }
    public void setCheckedBy(Long checkedBy) { this.checkedBy = checkedBy; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
