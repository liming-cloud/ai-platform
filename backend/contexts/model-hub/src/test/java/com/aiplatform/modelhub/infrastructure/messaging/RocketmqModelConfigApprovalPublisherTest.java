package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.ModelApprovalTestFixtures;
import com.aiplatform.modelhub.domain.ModelApprovalCancellation;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.infrastructure.messaging.publisher.RocketmqModelConfigApprovalPublisher;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelApprovalRequestOutboxMapper;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RocketmqModelConfigApprovalPublisherTest {
    @Test
    void publishesModelConfigApprovalRequestedDomainEvent() throws Exception {
        ModelApprovalRequestOutboxMapper outbox = mock(ModelApprovalRequestOutboxMapper.class);
        when(outbox.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RocketmqModelConfigApprovalPublisher publisher = new RocketmqModelConfigApprovalPublisher(
                outbox,
                new JsonMapper()
        );

        String requestId = publisher.requestApproval(ModelApprovalTestFixtures.configChange(
                "2001", "REQ-1001", "3001", "4001", "1001", "TRACE-REQ-001",
                Instant.parse("2026-05-25T08:00:00Z")
        ));

        assertThat(requestId).isEqualTo("REQ-1001");
        ArgumentCaptor<DomainEventMessage> messageCaptor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(messageCaptor.capture());
        DomainEventMessage message = messageCaptor.getValue();
        assertThat(message.eventType()).isEqualTo("ModelConfigApprovalRequested");
        assertThat(message.tenantId()).isEqualTo("2001");
        assertThat(message.aggregateType()).isEqualTo("ModelConfig");
        assertThat(message.aggregateId()).isEqualTo("4001");
        assertThat(message.traceId()).isEqualTo("TRACE-REQ-001");
        JsonNode payload = new JsonMapper().readTree(message.payloadJson());
        assertThat(payload.get("approvalRequestId").asText()).isEqualTo("REQ-1001");
        assertThat(payload.get("approvalType").asText()).isEqualTo("model_config_change");
        assertThat(payload.get("providerId").asText()).isEqualTo("3001");
        assertThat(payload.get("modelConfigId").asText()).isEqualTo("4001");
        assertThat(payload.get("sourceResourceType").asText()).isEqualTo("model_config");
        assertThat(payload.get("sourceResourceId").asText()).isEqualTo("4001");
        assertThat(payload.get("riskLevel").asText()).isEqualTo("HIGH_RISK");
    }

    @Test
    void publishesModelProviderApprovalRequestedDomainEventWhenModelConfigIdIsNull() throws Exception {
        ModelApprovalRequestOutboxMapper outbox = mock(ModelApprovalRequestOutboxMapper.class);
        when(outbox.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RocketmqModelConfigApprovalPublisher publisher = new RocketmqModelConfigApprovalPublisher(
                outbox,
                new JsonMapper()
        );

        String requestId = publisher.requestApproval(ModelApprovalTestFixtures.providerChange(
                "2001", "REQ-PROVIDER-3009", "3009", "SiliconFlow", "1001", "TRACE-PROVIDER-001",
                Instant.parse("2026-06-06T10:00:00Z")
        ));

        assertThat(requestId).isNotBlank();
        ArgumentCaptor<DomainEventMessage> messageCaptor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(messageCaptor.capture());
        DomainEventMessage message = messageCaptor.getValue();
        assertThat(message.aggregateType()).isEqualTo("ModelProvider");
        assertThat(message.aggregateId()).isEqualTo("3009");
        JsonNode payload = new JsonMapper().readTree(message.payloadJson());
        assertThat(payload.get("approvalType").asText()).isEqualTo("model_provider_change");
        assertThat(payload.get("providerId").asText()).isEqualTo("3009");
        assertThat(payload.get("modelConfigId").isNull()).isTrue();
        assertThat(payload.get("sourceResourceType").asText()).isEqualTo("model_provider");
        assertThat(payload.get("sourceResourceId").asText()).isEqualTo("3009");
    }

    @Test
    void failsClosedWhenApprovalRequestCannotBeEnqueued() {
        ModelApprovalRequestOutboxMapper outbox = mock(ModelApprovalRequestOutboxMapper.class);
        RocketmqModelConfigApprovalPublisher publisher = new RocketmqModelConfigApprovalPublisher(
                outbox,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.requestApproval(ModelApprovalTestFixtures.providerChange(
                "2001", "REQ-PROVIDER-3009", "3009", "SiliconFlow", "1001", "TRACE-PROVIDER-001",
                Instant.parse("2026-06-06T10:00:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模型审批请求 outbox 写入失败");
    }

    @Test
    void enqueuesApprovalCancellationWithDeterministicIdentityAndTruthfulPayload() throws Exception {
        ModelApprovalRequestOutboxMapper outbox = mock(ModelApprovalRequestOutboxMapper.class);
        when(outbox.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RocketmqModelConfigApprovalPublisher publisher = new RocketmqModelConfigApprovalPublisher(
                outbox,
                new JsonMapper()
        );
        Instant requestedAt = Instant.parse("2026-07-21T08:00:00Z");

        publisher.cancelApproval(new ModelApprovalCancellation(
                TenantId.of("2001"),
                "REQ-PROVIDER-3009",
                ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                "3009",
                "1001",
                "供应商地址录入错误",
                "TRACE-CANCEL-001",
                requestedAt
        ));

        ArgumentCaptor<DomainEventMessage> messageCaptor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(messageCaptor.capture());
        DomainEventMessage message = messageCaptor.getValue();
        assertThat(message.messageId())
                .isEqualTo("ModelApprovalCancellationRequested:2001:model_provider:REQ-PROVIDER-3009");
        assertThat(message.eventType()).isEqualTo("ModelApprovalCancellationRequested");
        assertThat(message.aggregateType()).isEqualTo("ModelProvider");
        assertThat(message.aggregateId()).isEqualTo("3009");
        assertThat(message.schemaVersion()).isEqualTo("v1");
        assertThat(message.version()).isEqualTo(1);
        assertThat(message.occurredAt()).isEqualTo(requestedAt);
        assertThat(message.traceId()).isEqualTo("TRACE-CANCEL-001");
        JsonNode payload = new JsonMapper().readTree(message.payloadJson());
        assertThat(payload.get("approvalRequestId").asText()).isEqualTo("REQ-PROVIDER-3009");
        assertThat(payload.get("sourceContext").asText()).isEqualTo("model-hub");
        assertThat(payload.get("sourceResourceType").asText()).isEqualTo("model_provider");
        assertThat(payload.get("sourceResourceId").asText()).isEqualTo("3009");
        assertThat(payload.get("operatorId").asText()).isEqualTo("1001");
        assertThat(payload.get("reason").asText()).isEqualTo("供应商地址录入错误");
        assertThat(payload.get("requestedAt").asText()).isEqualTo(requestedAt.toString());
    }

    @Test
    void canonicalizesNanosecondCancellationBeforeWritingEnvelopeAndPayloadToOutbox() throws Exception {
        ModelApprovalRequestOutboxMapper outbox = mock(ModelApprovalRequestOutboxMapper.class);
        when(outbox.enqueue(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RocketmqModelConfigApprovalPublisher publisher = new RocketmqModelConfigApprovalPublisher(
                outbox,
                new JsonMapper()
        );
        Instant sourceClock = Instant.parse("2026-07-21T08:00:00.123456789Z");
        Instant postgresPrecision = sourceClock.truncatedTo(ChronoUnit.MICROS);

        publisher.cancelApproval(new ModelApprovalCancellation(
                TenantId.of("2001"),
                "REQ-PROVIDER-3009",
                ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                "3009",
                "1001",
                "供应商地址录入错误",
                "TRACE-CANCEL-NANOS",
                sourceClock
        ));

        ArgumentCaptor<DomainEventMessage> messageCaptor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(messageCaptor.capture());
        DomainEventMessage message = messageCaptor.getValue();
        assertThat(message.occurredAt()).isEqualTo(postgresPrecision);
        assertThat(new JsonMapper().readTree(message.payloadJson()).get("requestedAt").asText())
                .isEqualTo(postgresPrecision.toString());
    }

    @Test
    void cancellationFailsClosedWhenOutboxDoesNotPersistExactlyOneRow() {
        ModelApprovalRequestOutboxMapper outbox = mock(ModelApprovalRequestOutboxMapper.class);
        RocketmqModelConfigApprovalPublisher publisher = new RocketmqModelConfigApprovalPublisher(
                outbox,
                new JsonMapper()
        );

        assertThatThrownBy(() -> publisher.cancelApproval(new ModelApprovalCancellation(
                TenantId.of("2001"),
                "REQ-PROVIDER-3009",
                ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                "3009",
                "1001",
                "供应商地址录入错误",
                "TRACE-CANCEL-001",
                Instant.parse("2026-07-21T08:00:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模型审批撤销 outbox 写入失败");
    }
}
