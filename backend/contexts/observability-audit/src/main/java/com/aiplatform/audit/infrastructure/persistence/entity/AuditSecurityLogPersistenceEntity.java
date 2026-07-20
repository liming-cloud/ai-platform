package com.aiplatform.audit.infrastructure.persistence.entity;

import com.aiplatform.audit.infrastructure.persistence.entity.support.AuditNumericIdParser;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.Instant;
import java.util.Objects;

/**
 * `audit_security_log` 表写入实体，承接登录、登出、权限和敏感访问审计事件。
 */
public class AuditSecurityLogPersistenceEntity {
    @TableField("id")
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("actor_id")
    private Long actorId;

    @TableField("event_type")
    private String eventType;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("subject_type")
    private String subjectType;

    @TableField("subject_id")
    private Long subjectId;

    @TableField("subject_ref")
    private String subjectRef;

    @TableField("result_status")
    private String resultStatus;

    @TableField("detail_json")
    private String detailJson;

    @TableField("trace_id")
    private String traceId;

    @TableField("created_at")
    private Instant createdAt;

    public static AuditSecurityLogPersistenceEntity fromEvent(
            AuditEvent event,
            String resultStatus,
            String detailJson
    ) {
        AuditSecurityLogPersistenceEntity entity = baseEntity(event, resultStatus, detailJson);
        entity.setSubjectId(AuditNumericIdParser.projectOptionalId(event.resourceId()));
        return entity;
    }

    public static AuditSecurityLogPersistenceEntity fromEventWithoutSubjectId(
            AuditEvent event,
            String resultStatus,
            String detailJson
    ) {
        return baseEntity(event, resultStatus, detailJson);
    }

    private static AuditSecurityLogPersistenceEntity baseEntity(
            AuditEvent event,
            String resultStatus,
            String detailJson
    ) {
        Objects.requireNonNull(event, "审计事件不能为空");
        AuditSecurityLogPersistenceEntity entity = new AuditSecurityLogPersistenceEntity();
        entity.setTenantId(AuditNumericIdParser.parseRequiredId(event.tenantId(), "租户 ID"));
        entity.setActorId(AuditNumericIdParser.parseOptionalId(event.actorId(), "操作人 ID"));
        entity.setEventType(event.action());
        entity.setRiskLevel(event.riskLevel());
        entity.setSubjectType(event.resourceType());
        entity.setSubjectRef(event.resourceId());
        entity.setResultStatus(resultStatus);
        entity.setDetailJson(detailJson);
        entity.setTraceId(event.traceId());
        entity.setCreatedAt(event.createdAt());
        return entity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectRef() {
        return subjectRef;
    }

    public void setSubjectRef(String subjectRef) {
        this.subjectRef = subjectRef;
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
