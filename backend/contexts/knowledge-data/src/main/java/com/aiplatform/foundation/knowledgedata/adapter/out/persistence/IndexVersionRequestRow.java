package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

/**
 * 索引版本幂等请求数据库行。
 */
public final class IndexVersionRequestRow {

    private String requestFingerprint;
    private String knowledgeSpaceId;
    private String indexVersionId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
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
}
