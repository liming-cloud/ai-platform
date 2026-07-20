package com.aiplatform.knowledge.infrastructure.persistence.projection;

import java.time.Instant;

/**
 * 检索 SQL 返回行，包含切片字段、召回分数和排序名次。
 */
public class KbDocumentChunkRetrievalRow {
    private Long id;
    private Long tenantId;
    private Long knowledgeBaseId;
    private Long documentId;
    private String content;
    private String sourceUri;
    private Integer documentVersion;
    private String titlePath;
    private Integer paragraphNo;
    private String tableCellRange;
    private String classification;
    private String visibility;
    private String aclTokens;
    private Boolean authoritative;
    private Instant publishedAt;
    private Instant expiresAt;
    private String embeddingModel;
    private String chunkStrategyVersion;
    private String indexVersion;
    private Double score;
    private Integer rank;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public Integer getDocumentVersion() {
        return documentVersion;
    }

    public void setDocumentVersion(Integer documentVersion) {
        this.documentVersion = documentVersion;
    }

    public String getTitlePath() {
        return titlePath;
    }

    public void setTitlePath(String titlePath) {
        this.titlePath = titlePath;
    }

    public Integer getParagraphNo() {
        return paragraphNo;
    }

    public void setParagraphNo(Integer paragraphNo) {
        this.paragraphNo = paragraphNo;
    }

    public String getTableCellRange() {
        return tableCellRange;
    }

    public void setTableCellRange(String tableCellRange) {
        this.tableCellRange = tableCellRange;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getAclTokens() {
        return aclTokens;
    }

    public void setAclTokens(String aclTokens) {
        this.aclTokens = aclTokens;
    }

    public Boolean getAuthoritative() {
        return authoritative;
    }

    public void setAuthoritative(Boolean authoritative) {
        this.authoritative = authoritative;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getChunkStrategyVersion() {
        return chunkStrategyVersion;
    }

    public void setChunkStrategyVersion(String chunkStrategyVersion) {
        this.chunkStrategyVersion = chunkStrategyVersion;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }
}
