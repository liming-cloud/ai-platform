package com.aiplatform.infra.rocketmq.model;

import java.time.Instant;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * 跨上下文领域事件 envelope，承载 Spring Cloud Stream 发布所需的路由、追踪和 schema 事实。
 */
public record DomainEventMessage(
        String messageId,
        String eventType,
        String tenantId,
        String aggregateType,
        String aggregateId,
        long version,
        String schemaVersion,
        @JsonSerialize(using = ExactInstantWireSerializer.class) Instant occurredAt,
        String traceId,
        String producer,
        String payloadJson
) {
    public DomainEventMessage {
        messageId = requireText(messageId, "消息 ID");
        eventType = requireText(eventType, "事件类型");
        tenantId = requireText(tenantId, "租户 ID");
        aggregateType = requireText(aggregateType, "聚合类型");
        aggregateId = requireText(aggregateId, "聚合 ID");
        if (version <= 0) {
            throw new IllegalArgumentException("事件版本必须大于 0");
        }
        schemaVersion = requireText(schemaVersion, "事件协议版本");
        occurredAt = requireValue(occurredAt, "事件发生时间");
        traceId = requireText(traceId, "链路 ID");
        producer = requireText(producer, "生产者");
        payloadJson = requireText(payloadJson, "事件载荷");
    }

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
}
