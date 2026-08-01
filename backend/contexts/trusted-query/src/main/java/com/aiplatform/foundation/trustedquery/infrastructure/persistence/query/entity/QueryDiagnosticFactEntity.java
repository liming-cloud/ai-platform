package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity;

/**
 * 查询终态固化的最小诊断事实持久化读取模型。
 */
public final class QueryDiagnosticFactEntity {

    private String candidateCollection;
    private Integer candidateCount;
    private String retrievalAuthorization;
    private long chatModelRevision;
    private long embeddingModelRevision;
    private String citationValidation;

    public String getCandidateCollection() {
        return candidateCollection;
    }

    public void setCandidateCollection(String candidateCollection) {
        this.candidateCollection = candidateCollection;
    }

    public Integer getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(Integer candidateCount) {
        this.candidateCount = candidateCount;
    }

    public String getRetrievalAuthorization() {
        return retrievalAuthorization;
    }

    public void setRetrievalAuthorization(String retrievalAuthorization) {
        this.retrievalAuthorization = retrievalAuthorization;
    }

    public long getChatModelRevision() {
        return chatModelRevision;
    }

    public void setChatModelRevision(long chatModelRevision) {
        this.chatModelRevision = chatModelRevision;
    }

    public long getEmbeddingModelRevision() {
        return embeddingModelRevision;
    }

    public void setEmbeddingModelRevision(long embeddingModelRevision) {
        this.embeddingModelRevision = embeddingModelRevision;
    }

    public String getCitationValidation() {
        return citationValidation;
    }

    public void setCitationValidation(String citationValidation) {
        this.citationValidation = citationValidation;
    }
}
