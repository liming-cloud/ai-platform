package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 草稿助手测试终态绑定的最小模型调用轨迹持久化行。
 */
@TableName("assistant_delivery.assistant_test_run_model_invocation_trace")
public final class AssistantTestRunModelInvocationTraceEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("assistant_test_run_id")
    private Long assistantTestRunId;

    @TableField("attempt_sequence")
    private int attemptSequence;

    @TableField("configuration_id")
    private Long configurationId;

    @TableField("configuration_revision")
    private long configurationRevision;

    @TableField("catalog_entry_id")
    private Long catalogEntryId;

    @TableField("catalog_entry_revision")
    private long catalogEntryRevision;

    @TableField("outcome")
    private String outcome;

    @TableField("input_tokens")
    private int inputTokens;

    @TableField("output_tokens")
    private int outputTokens;

    @TableField("total_tokens")
    private int totalTokens;

    @TableField("latency_millis")
    private long latencyMillis;

    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long value) {
        tenantId = value;
    }

    public Long getAssistantTestRunId() {
        return assistantTestRunId;
    }

    public void setAssistantTestRunId(Long value) {
        assistantTestRunId = value;
    }

    public int getAttemptSequence() {
        return attemptSequence;
    }

    public void setAttemptSequence(int value) {
        attemptSequence = value;
    }

    public Long getConfigurationId() {
        return configurationId;
    }

    public void setConfigurationId(Long value) {
        configurationId = value;
    }

    public long getConfigurationRevision() {
        return configurationRevision;
    }

    public void setConfigurationRevision(long value) {
        configurationRevision = value;
    }

    public Long getCatalogEntryId() {
        return catalogEntryId;
    }

    public void setCatalogEntryId(Long value) {
        catalogEntryId = value;
    }

    public long getCatalogEntryRevision() {
        return catalogEntryRevision;
    }

    public void setCatalogEntryRevision(long value) {
        catalogEntryRevision = value;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String value) {
        outcome = value;
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

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public void setLatencyMillis(long value) {
        latencyMillis = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        createdAt = value;
    }
}
