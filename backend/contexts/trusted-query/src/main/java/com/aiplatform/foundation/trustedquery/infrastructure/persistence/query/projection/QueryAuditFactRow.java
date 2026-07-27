package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection;

import java.time.Instant;

/**
 * 不读取问题正文、回答和引用内容的查询审计数据库行。
 */
public final class QueryAuditFactRow {

    private String queryRunId;
    private String requestedBy;
    private String assistantVersionId;
    private String questionDigest;
    private String status;
    private String refusalReason;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private Instant createdAt;
    private Instant completedAt;

    public String getQueryRunId() { return queryRunId; }
    public void setQueryRunId(String value) { queryRunId = value; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String value) { requestedBy = value; }
    public String getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(String value) { assistantVersionId = value; }
    public String getQuestionDigest() { return questionDigest; }
    public void setQuestionDigest(String value) { questionDigest = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRefusalReason() { return refusalReason; }
    public void setRefusalReason(String value) { refusalReason = value; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int value) { inputTokens = value; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int value) { outputTokens = value; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int value) { totalTokens = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
