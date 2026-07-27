package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection;

/**
 * 查询幂等请求数据库行。
 */
public final class QueryRunRequestRow {

    private String requestFingerprint;
    private String queryRunId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String value) {
        requestFingerprint = value;
    }

    public String getQueryRunId() {
        return queryRunId;
    }

    public void setQueryRunId(String value) {
        queryRunId = value;
    }
}
