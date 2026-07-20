package com.aiplatform.summary.infrastructure.persistence.entity;

import java.time.Instant;

public class SummaryMetricSnapshotPersistenceEntity {
    private Long id;
    private Long tenantId;
    private String scopeType;
    private String metricCode;
    private String metricPayload;
    private String freshness;
    private Boolean partial;
    private String failedReason;
    private Instant asOf;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getMetricPayload() { return metricPayload; }
    public void setMetricPayload(String metricPayload) { this.metricPayload = metricPayload; }
    public String getFreshness() { return freshness; }
    public void setFreshness(String freshness) { this.freshness = freshness; }
    public Boolean getPartial() { return partial; }
    public void setPartial(Boolean partial) { this.partial = partial; }
    public String getFailedReason() { return failedReason; }
    public void setFailedReason(String failedReason) { this.failedReason = failedReason; }
    public Instant getAsOf() { return asOf; }
    public void setAsOf(Instant asOf) { this.asOf = asOf; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
