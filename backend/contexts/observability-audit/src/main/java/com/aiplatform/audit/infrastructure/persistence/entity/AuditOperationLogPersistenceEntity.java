package com.aiplatform.audit.infrastructure.persistence.entity;

import com.aiplatform.audit.infrastructure.persistence.entity.support.AuditNumericIdParser;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.Instant;
import java.util.Objects;

/**
 * `audit_operation_log` 表写入实体，承接非安全类业务操作审计事件。
 */
public class AuditOperationLogPersistenceEntity {
    @TableField("tenant_id")
    private Long tenantId;

    @TableField("actor_id")
    private Long actorId;

    @TableField("action_code")
    private String actionCode;

    @TableField("resource_type")
    private String resourceType;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("resource_ref")
    private String resourceRef;

    @TableField("result_status")
    private String resultStatus;

    @TableField("detail_json")
    private String detailJson;

    @TableField("trace_id")
    private String traceId;

    @TableField("created_at")
    private Instant createdAt;

    public static AuditOperationLogPersistenceEntity fromEvent(
            AuditEvent event,
            String resultStatus,
            String detailJson
    ) {
        Objects.requireNonNull(event, "审计事件不能为空");
        AuditOperationLogPersistenceEntity entity = new AuditOperationLogPersistenceEntity();
        entity.setTenantId(AuditNumericIdParser.parseRequiredId(event.tenantId(), "租户 ID"));
        entity.setActorId(AuditNumericIdParser.parseOptionalId(event.actorId(), "操作人 ID"));
        entity.setActionCode(event.action());
        entity.setResourceType(event.resourceType());
        entity.setResourceRef(event.resourceId());
        entity.setResourceId(AuditNumericIdParser.projectOptionalId(event.resourceId()));
        entity.setResultStatus(resultStatus);
        entity.setDetailJson(detailJson);
        entity.setTraceId(event.traceId());
        entity.setCreatedAt(event.createdAt());
        return entity;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceRef() {
        return resourceRef;
    }

    public void setResourceRef(String resourceRef) {
        this.resourceRef = resourceRef;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
