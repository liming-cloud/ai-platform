package com.aiplatform.knowledge.infrastructure.persistence.projection;

public class DocumentObjectCleanupRow {
    private Long id;
    private Long tenantId;
    private Long knowledgeBaseId;
    private Long documentId;
    private String bucket;
    private String objectKey;
    private String sourceUri;
    private String observationFailureType;
    private Integer attemptCount;
    private String leaseOwner;
    private String leaseToken;
    private Integer version;

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

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getObservationFailureType() {
        return observationFailureType;
    }

    public void setObservationFailureType(String observationFailureType) {
        this.observationFailureType = observationFailureType;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public void setLeaseToken(String leaseToken) {
        this.leaseToken = leaseToken;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
