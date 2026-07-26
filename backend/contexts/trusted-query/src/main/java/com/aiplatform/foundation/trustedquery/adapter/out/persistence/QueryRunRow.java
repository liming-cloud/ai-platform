package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

import java.time.Instant;

/**
 * 查询运行数据库行。
 */
public final class QueryRunRow {

    private String tenantId;
    private String id;
    private String assistantVersionId;
    private String requestedBy;
    private String questionDigest;
    private String status;
    private String answer;
    private String refusalReason;
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
    private long version;
    private Instant createdAt;
    private Instant completedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { tenantId = value; }
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(String value) { assistantVersionId = value; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String value) { requestedBy = value; }
    public String getQuestionDigest() { return questionDigest; }
    public void setQuestionDigest(String value) { questionDigest = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getAnswer() { return answer; }
    public void setAnswer(String value) { answer = value; }
    public String getRefusalReason() { return refusalReason; }
    public void setRefusalReason(String value) { refusalReason = value; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int value) { inputTokens = value; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int value) { outputTokens = value; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int value) { totalTokens = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
