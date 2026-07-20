package com.aiplatform.approval.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.approval.domain.event.ApprovalDecisionRecordedEvent;
import com.aiplatform.approval.infrastructure.messaging.publisher.RocketmqApprovalDecisionPublisher;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprovalDecisionOutboxMapper;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class RocketmqApprovalDecisionPublisherTest {
    @Test
    void writesOneDeterministicDecisionEnvelopeToTheDatabaseOutbox() {
        ApprovalDecisionOutboxMapper outbox = mock(ApprovalDecisionOutboxMapper.class);
        when(outbox.enqueue(any(DomainEventMessage.class))).thenReturn(1);
        RocketmqApprovalDecisionPublisher publisher = new RocketmqApprovalDecisionPublisher(outbox, new JsonMapper());

        publisher.publish(event());

        ArgumentCaptor<DomainEventMessage> captor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(captor.capture());
        DomainEventMessage message = captor.getValue();
        assertThat(message.messageId()).isEqualTo("ApprovalDecisionRecorded:2001:7001:APPROVED");
        assertThat(message.eventType()).isEqualTo("ApprovalDecisionRecorded");
        assertThat(message.schemaVersion()).isEqualTo("v3");
        assertThat(message.tenantId()).isEqualTo("2001");
        assertThat(message.payloadJson())
                .contains("\"taskId\":\"7001\"")
                .contains("\"approvalRequestId\":\"REQ-7001\"")
                .contains("\"sourceResourceId\":\"3001\"")
                .contains("\"approverId\":\"1002\"")
                .contains("\"evidenceSchema\":\"model-approval-evidence/v1\"")
                .contains("\"evidenceDigest\":\"" + "a".repeat(64) + "\"")
                .contains("\"targetDigest\":\"" + "b".repeat(64) + "\"");
    }

    @Test
    void outboxConflictFailsTheBusinessTransactionClosed() {
        ApprovalDecisionOutboxMapper outbox = mock(ApprovalDecisionOutboxMapper.class);
        when(outbox.enqueue(any(DomainEventMessage.class))).thenReturn(0);
        RocketmqApprovalDecisionPublisher publisher = new RocketmqApprovalDecisionPublisher(outbox, new JsonMapper());

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批决策 outbox 写入失败");
    }

    private static ApprovalDecisionRecordedEvent event() {
        return new ApprovalDecisionRecordedEvent(
                TenantId.of("2001"),
                "7001",
                "REQ-7001",
                "model-hub",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                "1002",
                "同意上线",
                "trace-approval-7001",
                "model-approval-evidence/v1",
                "a".repeat(64),
                "b".repeat(64),
                Instant.parse("2026-07-18T08:00:00Z")
        );
    }
}
