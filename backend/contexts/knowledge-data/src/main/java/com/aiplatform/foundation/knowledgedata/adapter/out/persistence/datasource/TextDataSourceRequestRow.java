package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.datasource;

/**
 * 幂等请求保存的历史结果快照。
 */
public final class TextDataSourceRequestRow {

    private String requestFingerprint;
    private String knowledgeSpaceId;
    private String dataSourceId;
    private String resultStatus;
    private long resultVersion;
    private int resultContentVersion;
    private int resultPublishedContentVersion;

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

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public long getResultVersion() {
        return resultVersion;
    }

    public void setResultVersion(long resultVersion) {
        this.resultVersion = resultVersion;
    }

    public int getResultContentVersion() {
        return resultContentVersion;
    }

    public void setResultContentVersion(int resultContentVersion) {
        this.resultContentVersion = resultContentVersion;
    }

    public int getResultPublishedContentVersion() {
        return resultPublishedContentVersion;
    }

    public void setResultPublishedContentVersion(int resultPublishedContentVersion) {
        this.resultPublishedContentVersion = resultPublishedContentVersion;
    }
}
