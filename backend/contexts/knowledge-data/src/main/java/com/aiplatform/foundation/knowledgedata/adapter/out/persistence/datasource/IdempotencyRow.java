package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.datasource;

/**
 * 已登记知识空间创建请求的最小事实。
 */
public class IdempotencyRow {

    private String requestFingerprint;
    private String knowledgeSpaceId;

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
}
