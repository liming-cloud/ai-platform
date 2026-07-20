package com.aiplatform.modelhub.presentation.messaging.consumer;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxService;
import com.aiplatform.modelhub.infrastructure.messaging.model.ApprovalDecisionPayload;
import com.aiplatform.modelhub.infrastructure.messaging.model.LegacyApprovalDecisionPayload;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 审批决策事件消费者，模型中心通过事件完成供应商和模型配置启用闭环。
 */
@Component("approvalDecisionRecorded")
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.approvalDecisionRecorded-in-0.destination")
public class ApprovalDecisionRecordedConsumer implements Consumer<DomainEventMessage> {
    private static final String EVENT_TYPE = "ApprovalDecisionRecorded";
    private static final String SOURCE_CONTEXT = "model-hub";
    private static final String PRODUCER = "approval-center";
    private static final String AGGREGATE_TYPE = "ApprovalTask";

    private final ApprovalDecisionInboxService inbox;
    private final JsonMapper objectMapper;

    public ApprovalDecisionRecordedConsumer(
            ApprovalDecisionInboxService inbox,
            JsonMapper objectMapper
    ) {
        this.inbox = Objects.requireNonNull(inbox, "审批决策 inbox 服务不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public void accept(DomainEventMessage message) {
        onMessage(message);
    }

    public void onMessage(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        if (!EVENT_TYPE.equals(message.eventType())) {
            return;
        }
        switch (message.schemaVersion()) {
            case "v3" -> processCurrent(message);
            case "v2" -> processCorrelatedLegacy(message);
            case "v1" -> processLegacy(message);
            default -> throw new IllegalArgumentException(
                    "审批决策事件 schemaVersion 不支持：" + message.schemaVersion()
            );
        }
    }

    private void processCurrent(DomainEventMessage message) {
        ApprovalDecisionPayload payload = payload(message, ApprovalDecisionPayload.class);
        validateEnvelope(message, payload.taskId(), payload.status());
        payload.requireCurrentEvidence();
        if (SOURCE_CONTEXT.equals(payload.sourceContext())) {
            inbox.process(message, payload);
        }
    }

    private void processCorrelatedLegacy(DomainEventMessage message) {
        ApprovalDecisionPayload payload = payload(message, ApprovalDecisionPayload.class);
        validateEnvelope(message, payload.taskId(), payload.status());
        if (SOURCE_CONTEXT.equals(payload.sourceContext())) {
            inbox.processLegacyCorrelated(message, payload);
        }
    }

    private void processLegacy(DomainEventMessage message) {
        LegacyApprovalDecisionPayload payload = payload(message, LegacyApprovalDecisionPayload.class);
        validateEnvelope(message, payload.taskId(), payload.status());
        if (SOURCE_CONTEXT.equals(payload.sourceContext())) {
            inbox.processLegacy(message, payload);
        }
    }

    private <T> T payload(DomainEventMessage message, Class<T> payloadType) {
        try {
            return objectMapper.readValue(message.payloadJson(), payloadType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("审批决策事件载荷解析失败", exception);
        }
    }

    private static void validateEnvelope(DomainEventMessage message, String taskId, String status) {
        String expectedMessageId = "%s:%s:%s:%s".formatted(
                EVENT_TYPE, message.tenantId(), taskId, status
        );
        if (!PRODUCER.equals(message.producer())
                || !AGGREGATE_TYPE.equals(message.aggregateType())
                || message.version() != 1
                || !message.aggregateId().equals(taskId)
                || !message.messageId().equals(expectedMessageId)) {
            throw new IllegalArgumentException("审批决策事件 envelope 与载荷不一致");
        }
    }
}
