package com.aiplatform.shared.audit.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditEvent {
    private final String eventId;
    private final String tenantId;
    private final String actorId;
    private final String resourceType;
    private final String resourceId;
    private final String action;
    private final String riskLevel;
    private final String approvalReferenceId;
    private final String traceId;
    private final Instant createdAt;
    private final Map<String, Object> detailAttributes;

    public AuditEvent(String eventId, String tenantId, String actorId, String resourceType, String resourceId,
            String action, String riskLevel, String approvalReferenceId, String traceId, Instant createdAt) {
        this(eventId, tenantId, actorId, resourceType, resourceId, action, riskLevel, approvalReferenceId,
                traceId, createdAt, Map.of());
    }

    public AuditEvent(String eventId, String tenantId, String actorId, String resourceType, String resourceId,
            String action, String riskLevel, String approvalReferenceId, String traceId, Instant createdAt,
            Map<String, Object> detailAttributes) {
        this.eventId = requireText(eventId, "审计事件 ID");
        this.tenantId = requireText(tenantId, "租户 ID");
        this.actorId = actorId;
        this.resourceType = requireText(resourceType, "审计资源类型");
        this.resourceId = resourceId;
        this.action = requireText(action, "审计动作");
        this.riskLevel = requireText(riskLevel, "审计风险等级");
        this.approvalReferenceId = approvalReferenceId;
        this.traceId = requireText(traceId, "调用链 ID");
        this.createdAt = requireValue(createdAt, "审计创建时间");
        this.detailAttributes = normalizeDetails(detailAttributes);
    }

    public String eventId() { return eventId; }
    public String tenantId() { return tenantId; }
    public String actorId() { return actorId; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String action() { return action; }
    public String riskLevel() { return riskLevel; }
    public String approvalReferenceId() { return approvalReferenceId; }
    public String traceId() { return traceId; }
    public Instant createdAt() { return createdAt; }
    public Map<String, Object> detailAttributes() { return detailAttributes; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static Map<String, Object> normalizeDetails(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = requireText(key, "审计详情字段名");
            if (value != null) {
                normalized.put(normalizedKey, value);
            }
        });
        return Collections.unmodifiableMap(normalized);
    }
}
