package com.aiplatform.modelhub.infrastructure.messaging.publisher;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.domain.ModelApprovalCancellation;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelApprovalRequestOutboxMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模型审批请求/撤销发布器：只在当前业务事务中入队，不直接调用消息网关。
 */
@Component
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class RocketmqModelConfigApprovalPublisher implements ModelConfigApprovalPort {
    private static final String REQUEST_EVENT_TYPE = "ModelConfigApprovalRequested";
    private static final String CANCELLATION_EVENT_TYPE = "ModelApprovalCancellationRequested";
    private static final String PRODUCER = "model-hub";

    private final ModelApprovalRequestOutboxMapper outbox;
    private final JsonMapper objectMapper;

    public RocketmqModelConfigApprovalPublisher(ModelApprovalRequestOutboxMapper outbox, JsonMapper objectMapper) {
        this.outbox = Objects.requireNonNull(outbox, "模型审批请求 outbox Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String requestApproval(ModelConfigChange change) {
        Objects.requireNonNull(change, "模型配置变更不能为空");
        DomainEventMessage message = new DomainEventMessage(
                "ModelConfigApprovalRequested:%s:%s:%s".formatted(
                        change.tenantId().value(),
                        change.sourceResourceType(),
                        change.changeId()
                ),
                REQUEST_EVENT_TYPE,
                change.tenantId().value(),
                aggregateType(change),
                change.sourceResourceId(),
                1,
                "v2",
                change.requestedAt(),
                change.traceId(),
                PRODUCER,
                payloadJson(change)
        );
        if (outbox.enqueue(message) != 1) {
            throw new IllegalStateException("模型审批请求 outbox 写入失败");
        }
        return change.changeId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelApproval(ModelApprovalCancellation cancellation) {
        Objects.requireNonNull(cancellation, "模型审批撤销请求不能为空");
        DomainEventMessage message = new DomainEventMessage(
                "%s:%s:%s:%s".formatted(
                        CANCELLATION_EVENT_TYPE,
                        cancellation.tenantId().value(),
                        cancellation.sourceResourceType(),
                        cancellation.approvalRequestId()
                ),
                CANCELLATION_EVENT_TYPE,
                cancellation.tenantId().value(),
                cancellationAggregateType(cancellation.sourceResourceType()),
                cancellation.sourceResourceId(),
                1,
                "v1",
                cancellation.requestedAt(),
                cancellation.traceId(),
                PRODUCER,
                cancellationPayloadJson(cancellation)
        );
        if (outbox.enqueue(message) != 1) {
            throw new IllegalStateException("模型审批撤销 outbox 写入失败");
        }
    }

    private String payloadJson(ModelConfigChange change) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("approvalRequestId", change.changeId());
            payload.put("approvalType", change.approvalType());
            payload.put("providerId", change.providerId());
            payload.put("modelConfigId", change.modelConfigId());
            payload.put("sourceResourceType", change.sourceResourceType());
            payload.put("sourceResourceId", change.sourceResourceId());
            payload.put("changeSummary", change.changeSummary());
            payload.put("riskLevel", change.riskLevel().name());
            payload.put("operatorId", change.operatorId());
            payload.put("evidenceSchema", change.approvalEvidence().schema());
            payload.put("evidenceDigest", change.approvalEvidence().digest());
            payload.put("targetDigest", change.targetDigest());
            payload.put("evidence", change.approvalEvidence().facts());
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型配置审批事件载荷序列化失败", exception);
        }
    }

    private String cancellationPayloadJson(ModelApprovalCancellation cancellation) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("approvalRequestId", cancellation.approvalRequestId());
            payload.put("sourceContext", "model-hub");
            payload.put("sourceResourceType", cancellation.sourceResourceType());
            payload.put("sourceResourceId", cancellation.sourceResourceId());
            payload.put("operatorId", cancellation.operatorId());
            payload.put("reason", cancellation.reason());
            payload.put("requestedAt", cancellation.requestedAt().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型审批撤销事件载荷序列化失败", exception);
        }
    }

    private static String aggregateType(ModelConfigChange change) {
        return switch (change.sourceResourceType()) {
            case ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER -> "ModelProvider";
            case ModelConfigChange.RESOURCE_TYPE_MODEL_CONFIG -> "ModelConfig";
            default -> throw new IllegalStateException("未知审批资源类型：" + change.sourceResourceType());
        };
    }

    private static String cancellationAggregateType(String sourceResourceType) {
        return switch (sourceResourceType) {
            case ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER -> "ModelProvider";
            case ModelConfigChange.RESOURCE_TYPE_MODEL_CONFIG -> "ModelConfig";
            default -> throw new IllegalStateException("未知审批撤销资源类型：" + sourceResourceType);
        };
    }
}
