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

    @TableField("outcome")
    private String outcome;

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

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String value) {
        outcome = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        createdAt = value;
    }
}
