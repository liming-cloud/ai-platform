package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection;

import java.time.Instant;

/**
 * 查询运行事件数据库行。
 */
public final class QueryRunEventRow {

    private String tenantId;
    private String queryRunId;
    private long sequence;
    private String eventType;
    private String status;
    private String answer;
    private String refusalReason;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private Instant occurredAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String value) {
        tenantId = value;
    }

    public String getQueryRunId() {
        return queryRunId;
    }

    public void setQueryRunId(String value) {
        queryRunId = value;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long value) {
        sequence = value;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String value) {
        eventType = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String value) {
        answer = value;
    }

    public String getRefusalReason() {
        return refusalReason;
    }

    public void setRefusalReason(String value) {
        refusalReason = value;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int value) {
        inputTokens = value;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int value) {
        outputTokens = value;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int value) {
        totalTokens = value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant value) {
        occurredAt = value;
    }
}
