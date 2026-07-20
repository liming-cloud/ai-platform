package com.aiplatform.approval.presentation.messaging.consumer;

import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalRequest;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.port.ApprovalPort;
import com.aiplatform.approval.infrastructure.messaging.model.ModelApprovalCancellationPayload;
import com.aiplatform.approval.infrastructure.messaging.model.ModelApprovalEvidenceContract;
import com.aiplatform.approval.infrastructure.messaging.model.ModelConfigApprovalPayload;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模型审批请求/撤销消费者，approval-center 只消费领域事件并在本上下文持久化审批事实。
 */
@Component("modelConfigApprovalRequested")
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.modelConfigApprovalRequested-in-0.destination")
public class ModelConfigApprovalRequestedConsumer implements Consumer<DomainEventMessage> {
    private static final String EVENT_TYPE = "ModelConfigApprovalRequested";
    private static final String CANCELLATION_EVENT_TYPE = "ModelApprovalCancellationRequested";
    private static final String SOURCE_CONTEXT = "model-hub";
    private static final String PRODUCER = "model-hub";
    private static final String SCHEMA_VERSION = "v2";
    private static final String RETIRED_SCHEMA_VERSION = "v1";
    private static final String RESOURCE_TYPE_PROVIDER = "model_provider";
    private static final String RESOURCE_TYPE_CONFIG = "model_config";

    private final ApprovalPort approvalPort;
    private final JsonMapper objectMapper;

    public ModelConfigApprovalRequestedConsumer(ApprovalPort approvalPort, JsonMapper objectMapper) {
        this.approvalPort = Objects.requireNonNull(approvalPort, "审批端口不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public void accept(DomainEventMessage message) {
        onMessage(message);
    }

    public void onMessage(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件不能为空");
        if (CANCELLATION_EVENT_TYPE.equals(message.eventType())) {
            cancelApproval(message);
            return;
        }
        if (!EVENT_TYPE.equals(message.eventType())) {
            return;
        }
        // v1 never carried immutable evidence. Acknowledge delayed broker replays without
        // rebuilding a ghost task; database migration retires any still-dispatchable v1 row.
        if (RETIRED_SCHEMA_VERSION.equals(message.schemaVersion())) {
            return;
        }
        ModelConfigApprovalPayload payload = payload(message);
        validateEnvelope(message, payload);
        var evidence = ModelApprovalEvidenceContract.validate(message, payload);
        approvalPort.requestApproval(new ApprovalRequest(
                TenantId.of(message.tenantId()),
                payload.approvalRequestId(),
                payload.approvalType(),
                SOURCE_CONTEXT,
                payload.sourceResourceType(),
                payload.sourceResourceId(),
                payload.changeSummary(),
                ApprovalRiskLevel.valueOf(payload.riskLevel()),
                payload.operatorId(),
                message.traceId(),
                evidence,
                payload.targetDigest()
        ));
    }

    private void cancelApproval(DomainEventMessage message) {
        ModelApprovalCancellationPayload payload = cancellationPayload(message);
        String requestId = requireText(payload.approvalRequestId());
        String sourceContext = requireText(payload.sourceContext());
        String resourceType = requireText(payload.sourceResourceType());
        String resourceId = requireText(payload.sourceResourceId());
        Instant requestedAt;
        try {
            requestedAt = Instant.parse(requireText(payload.requestedAt()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("模型审批撤销事件时间不合法", exception);
        }
        boolean consistentResource = switch (resourceType) {
            case RESOURCE_TYPE_PROVIDER -> "ModelProvider".equals(message.aggregateType());
            case RESOURCE_TYPE_CONFIG -> "ModelConfig".equals(message.aggregateType());
            default -> false;
        };
        String expectedMessageId = "%s:%s:%s:%s".formatted(
                CANCELLATION_EVENT_TYPE, message.tenantId(), resourceType, requestId
        );
        if (!PRODUCER.equals(message.producer())
                || !SOURCE_CONTEXT.equals(sourceContext)
                || !"v1".equals(message.schemaVersion())
                || message.version() != 1
                || !resourceId.equals(message.aggregateId())
                || !expectedMessageId.equals(message.messageId())
                || !requestedAt.equals(message.occurredAt())
                || !consistentResource) {
            throw new IllegalArgumentException("模型审批撤销事件 envelope 与载荷不一致");
        }
        approvalPort.cancelApproval(new ApprovalCancellationRequest(
                TenantId.of(message.tenantId()),
                message.messageId(),
                requestId,
                sourceContext,
                resourceType,
                resourceId,
                requireText(payload.operatorId()),
                requireText(payload.reason()),
                message.traceId(),
                requestedAt
        ));
    }

    private ModelConfigApprovalPayload payload(DomainEventMessage message) {
        try {
            return objectMapper.readValue(message.payloadJson(), ModelConfigApprovalPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("模型配置审批事件载荷解析失败", exception);
        }
    }

    private ModelApprovalCancellationPayload cancellationPayload(DomainEventMessage message) {
        try {
            return objectMapper.readValue(message.payloadJson(), ModelApprovalCancellationPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("模型审批撤销事件载荷解析失败", exception);
        }
    }

    private static void validateEnvelope(DomainEventMessage message, ModelConfigApprovalPayload payload) {
        String requestId = requireText(payload.approvalRequestId());
        String resourceType = requireText(payload.sourceResourceType());
        String resourceId = requireText(payload.sourceResourceId());
        String providerId = requireText(payload.providerId());
        boolean consistentResource = switch (resourceType) {
            case RESOURCE_TYPE_PROVIDER -> "ModelProvider".equals(message.aggregateType())
                    && resourceId.equals(providerId)
                    && payload.modelConfigId() == null
                    && "model_provider_change".equals(payload.approvalType());
            case RESOURCE_TYPE_CONFIG -> "ModelConfig".equals(message.aggregateType())
                    && resourceId.equals(requireText(payload.modelConfigId()))
                    && "model_config_change".equals(payload.approvalType());
            default -> false;
        };
        String expectedMessageId = "%s:%s:%s:%s".formatted(
                EVENT_TYPE, message.tenantId(), resourceType, requestId
        );
        if (!PRODUCER.equals(message.producer())
                || !SCHEMA_VERSION.equals(message.schemaVersion())
                || message.version() != 1
                || !resourceId.equals(message.aggregateId())
                || !expectedMessageId.equals(message.messageId())
                || !consistentResource) {
            throw new IllegalArgumentException("模型配置审批事件 envelope 与载荷不一致");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型配置审批事件 envelope 与载荷不一致");
        }
        return value.trim();
    }
}
