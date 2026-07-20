package com.aiplatform.appstudio.infrastructure.messaging.outbox;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.time.Instant;

public class ApplicationReleaseOutboxRecord {
    private Long id;
    private String eventId;
    private String eventType;
    private String tenantId;
    private String aggregateType;
    private String aggregateId;
    private Long eventVersion;
    private String schemaVersion;
    private Instant occurredAt;
    private String traceId;
    private String producer;
    private String payloadJson;
    private Integer attempts;

    public DomainEventMessage toDomainEventMessage() {
        return new DomainEventMessage(
                requireText(eventId, "事件 ID"),
                requireText(eventType, "事件类型"),
                requireText(tenantId, "租户 ID"),
                requireText(aggregateType, "聚合类型"),
                requireText(aggregateId, "聚合 ID"),
                requiredLong(eventVersion, "事件版本"),
                requireText(schemaVersion, "Schema 版本"),
                requireValue(occurredAt, "发生时间"),
                requireText(traceId, "Trace ID"),
                requireText(producer, "事件生产方"),
                requireText(payloadJson, "事件载荷")
        );
    }

    public long requiredId() { return requiredLong(id, "outbox ID"); }

    public int requiredAttempts() {
        if (attempts == null || attempts < 1) {
            throw new IllegalStateException("outbox 投递次数必须大于 0");
        }
        return attempts;
    }

    private static long requiredLong(Long value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalStateException(fieldName + "必须大于 0");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public Long getEventVersion() { return eventVersion; }
    public void setEventVersion(Long eventVersion) { this.eventVersion = eventVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getProducer() { return producer; }
    public void setProducer(String producer) { this.producer = producer; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
}
