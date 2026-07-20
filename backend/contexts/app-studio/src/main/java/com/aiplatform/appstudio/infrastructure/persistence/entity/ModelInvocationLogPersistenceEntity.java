package com.aiplatform.appstudio.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.appstudio.domain.port.chat.model.ModelInvocationAuditRecord;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 模型调用日志持久化实体，对应平台统一模型调用审计表。
 */
@TableName("mdl_invocation_log")
public class ModelInvocationLogPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("trace_id")
    private String traceId;
    @TableField("idempotency_key")
    private String idempotencyKey;
    @TableField("source_context")
    private String sourceContext;
    @TableField("source_resource_type")
    private String sourceResourceType;
    @TableField("source_resource_id")
    private Long sourceResourceId;
    @TableField("model_config_id")
    private Long modelConfigId;
    @TableField("request_hash")
    private String requestHash;
    @TableField("input_tokens")
    private Integer inputTokens;
    @TableField("output_tokens")
    private Integer outputTokens;
    @TableField("total_cost")
    private BigDecimal totalCost;
    @TableField("latency_ms")
    private Integer latencyMs;
    @TableField("status")
    private String status;
    @TableField("error_code")
    private String errorCode;
    @TableField("error_message")
    private String errorMessage;
    @TableField("created_at")
    private Instant createdAt;

    public static ModelInvocationLogPersistenceEntity fromRecord(ModelInvocationAuditRecord record) {
        ModelInvocationLogPersistenceEntity entity = new ModelInvocationLogPersistenceEntity();
        entity.setTenantId(parseRequiredLong(record.tenantId().value(), "租户 ID"));
        entity.setTraceId(record.traceId());
        entity.setIdempotencyKey(record.idempotencyKey());
        entity.setSourceContext(record.sourceContext());
        entity.setSourceResourceType(record.sourceResourceType());
        entity.setSourceResourceId(record.sourceResourceId() == null ? null : parseRequiredLong(record.sourceResourceId(), "来源资源 ID"));
        entity.setModelConfigId(parseRequiredLong(record.modelConfigId(), "模型配置 ID"));
        entity.setRequestHash(record.requestHash());
        entity.setInputTokens(record.inputTokens());
        entity.setOutputTokens(record.outputTokens());
        entity.setTotalCost(BigDecimal.ZERO);
        entity.setLatencyMs(record.latencyMs());
        entity.setStatus(record.status());
        entity.setErrorCode(record.errorCode());
        entity.setErrorMessage(record.errorMessage());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }
    public String getSourceResourceType() { return sourceResourceType; }
    public void setSourceResourceType(String sourceResourceType) { this.sourceResourceType = sourceResourceType; }
    public Long getSourceResourceId() { return sourceResourceId; }
    public void setSourceResourceId(Long sourceResourceId) { this.sourceResourceId = sourceResourceId; }
    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
