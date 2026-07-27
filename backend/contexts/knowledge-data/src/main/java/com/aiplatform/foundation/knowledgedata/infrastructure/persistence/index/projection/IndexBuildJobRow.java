package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.projection;

/**
 * 可认领索引构建作业的最小数据库行。
 */
public final class IndexBuildJobRow {

    private String tenantId;
    private String knowledgeSpaceId;
    private String indexVersionId;
    private int attempt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public void setKnowledgeSpaceId(String knowledgeSpaceId) {
        this.knowledgeSpaceId = knowledgeSpaceId;
    }

    public String getIndexVersionId() {
        return indexVersionId;
    }

    public void setIndexVersionId(String indexVersionId) {
        this.indexVersionId = indexVersionId;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }
}
