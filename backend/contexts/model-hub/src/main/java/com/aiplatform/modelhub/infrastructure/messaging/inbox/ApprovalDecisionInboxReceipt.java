package com.aiplatform.modelhub.infrastructure.messaging.inbox;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.infrastructure.messaging.model.ApprovalDecisionPayload;
import java.time.Instant;
import java.util.Objects;

/**
 * 审批决策消费收据只保存幂等所需元数据，不复制可能敏感的消息载荷。
 */
public record ApprovalDecisionInboxReceipt(
        String eventId,
        String eventType,
        long tenantId,
        long taskId,
        String approvalRequestId,
        String sourceResourceType,
        long sourceResourceId,
        String traceId,
        Instant occurredAt
) {
    public ApprovalDecisionInboxReceipt {
        eventId = requireText(eventId, "事件 ID");
        eventType = requireText(eventType, "事件类型");
        if (tenantId <= 0 || taskId <= 0 || sourceResourceId <= 0) {
            throw new IllegalArgumentException("审批决策 inbox 数据库 ID 必须大于 0");
        }
        approvalRequestId = requireText(approvalRequestId, "审批请求 ID");
        sourceResourceType = requireText(sourceResourceType, "来源资源类型");
        traceId = requireText(traceId, "调用链 ID");
        occurredAt = Objects.requireNonNull(occurredAt, "事件发生时间不能为空");
    }

    public static ApprovalDecisionInboxReceipt from(
            DomainEventMessage message,
            ApprovalDecisionPayload payload
    ) {
        Objects.requireNonNull(message, "审批决策事件不能为空");
        Objects.requireNonNull(payload, "审批决策载荷不能为空");
        return new ApprovalDecisionInboxReceipt(
                message.messageId(),
                message.eventType(),
                parseRequiredLong(message.tenantId(), "租户 ID"),
                parseRequiredLong(payload.taskId(), "审批任务 ID"),
                payload.approvalRequestId(),
                payload.sourceResourceType(),
                parseRequiredLong(payload.sourceResourceId(), "来源资源 ID"),
                message.traceId(),
                message.occurredAt()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
