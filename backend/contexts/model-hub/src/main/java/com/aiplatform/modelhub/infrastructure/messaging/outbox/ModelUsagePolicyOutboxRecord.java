package com.aiplatform.modelhub.infrastructure.messaging.outbox;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.time.Instant;

/**
 * 已由 PostgreSQL lease claim 的模型策略事件快照。
 */
public class ModelUsagePolicyOutboxRecord {
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

    public ModelUsagePolicyOutboxRecord() {
    }

    public ModelUsagePolicyOutboxRecord(
            long id,
            String eventId,
            String eventType,
            String tenantId,
            String aggregateType,
            String aggregateId,
            long eventVersion,
            String schemaVersion,
            Instant occurredAt,
            String traceId,
            String producer,
            String payloadJson,
            int attempts
    ) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventVersion = eventVersion;
        this.schemaVersion = schemaVersion;
        this.occurredAt = occurredAt;
        this.traceId = traceId;
        this.producer = producer;
        this.payloadJson = payloadJson;
        this.attempts = attempts;
    }

    public DomainEventMessage toDomainEventMessage() {
        return new DomainEventMessage(
                eventId,
                eventType,
                tenantId,
                aggregateType,
                aggregateId,
                requiredLong(eventVersion, "事件版本"),
                schemaVersion,
                occurredAt,
                traceId,
                producer,
                payloadJson
        );
    }

    public long requiredId() {
        return requiredLong(id, "outbox ID");
    }

    public int requiredAttempts() {
        if (attempts == null || attempts < 1) {
            throw new IllegalStateException("outbox 投递次数必须大于 0");
        }
        return attempts;
    }

    private static long requiredLong(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(fieldName + "必须大于 0");
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
