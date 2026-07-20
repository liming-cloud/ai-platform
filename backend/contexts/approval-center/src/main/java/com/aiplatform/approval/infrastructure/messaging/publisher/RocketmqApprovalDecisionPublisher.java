package com.aiplatform.approval.infrastructure.messaging.publisher;

import com.aiplatform.approval.domain.event.ApprovalDecisionRecordedEvent;
import com.aiplatform.approval.domain.port.ApprovalDecisionEventPublisher;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprovalDecisionOutboxMapper;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批决策事件发布器，只在业务事务内写入数据库 outbox，不接触消息代理。
 */
@Component
public class RocketmqApprovalDecisionPublisher implements ApprovalDecisionEventPublisher {
    private static final String EVENT_TYPE = "ApprovalDecisionRecorded";
    private static final String AGGREGATE_TYPE = "ApprovalTask";
    private static final String PRODUCER = "approval-center";

    private final ApprovalDecisionOutboxMapper outbox;
    private final JsonMapper objectMapper;

    public RocketmqApprovalDecisionPublisher(ApprovalDecisionOutboxMapper outbox, JsonMapper objectMapper) {
        this.outbox = Objects.requireNonNull(outbox, "审批决策 outbox 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ApprovalDecisionRecordedEvent event) {
        Objects.requireNonNull(event, "审批决策事件不能为空");
        DomainEventMessage message = new DomainEventMessage(
                "ApprovalDecisionRecorded:%s:%s:%s".formatted(
                        event.tenantId().value(),
                        event.taskId(),
                        event.status()
                ),
                EVENT_TYPE,
                event.tenantId().value(),
                AGGREGATE_TYPE,
                event.taskId(),
                1,
                "v3",
                event.occurredAt(),
                event.traceId(),
                PRODUCER,
                payloadJson(event)
        );
        if (outbox.enqueue(message) != 1) {
            throw new IllegalStateException("审批决策 outbox 写入失败");
        }
    }

    private String payloadJson(ApprovalDecisionRecordedEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.ofEntries(
                    Map.entry("taskId", event.taskId()),
                    Map.entry("approvalRequestId", event.approvalRequestId()),
                    Map.entry("sourceContext", event.sourceContext()),
                    Map.entry("sourceResourceType", event.sourceResourceType()),
                    Map.entry("sourceResourceId", event.sourceResourceId()),
                    Map.entry("status", event.status()),
                    Map.entry("action", event.action()),
                    Map.entry("approverId", event.approverId()),
                    Map.entry("comment", event.comment()),
                    Map.entry("evidenceSchema", event.evidenceSchema()),
                    Map.entry("evidenceDigest", event.evidenceDigest()),
                    Map.entry("targetDigest", event.targetDigest())
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("审批决策事件载荷序列化失败", exception);
        }
    }
}
