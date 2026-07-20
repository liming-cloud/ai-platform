package com.aiplatform.modelhub.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.application.ModelApprovalDecisionApplicationService;
import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxReceipt;
import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxService;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ApprovalDecisionInboxMapper;
import com.aiplatform.modelhub.presentation.messaging.consumer.ApprovalDecisionRecordedConsumer;
import com.aiplatform.shared.kernel.TenantId;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApprovalDecisionRecordedConsumerTest {
    @Test
    void consumesProviderApprovalDecisionEventAsPresentationEntryPoint() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);
        Instant occurredAt = Instant.parse("2026-05-25T08:00:00Z");

        consumer.onMessage(message(
                "ApprovalDecisionRecorded",
                "model-hub",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                occurredAt
        ));

        verify(service).applyProviderDecision(
                TenantId.of("2001"), "3001", "REQ-9001", "model-approval-evidence/v1",
                "a".repeat(64), "b".repeat(64), true, occurredAt
        );
    }

    @Test
    void consumesModelConfigApprovalDecisionEventAsPresentationEntryPoint() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);
        Instant occurredAt = Instant.parse("2026-05-25T08:00:00Z");

        consumer.onMessage(message(
                "ApprovalDecisionRecorded",
                "model-hub",
                "model_config",
                "4001",
                "APPROVED",
                "APPROVE",
                occurredAt
        ));

        verify(service).applyModelConfigDecision(
                TenantId.of("2001"), "4001", "REQ-9001", "model-approval-evidence/v1",
                "a".repeat(64), "b".repeat(64), true, occurredAt
        );
    }

    @Test
    void consumesRejectedApprovalDecisionAsDisableDecision() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);
        Instant occurredAt = Instant.parse("2026-05-25T08:00:00Z");

        consumer.onMessage(message(
                "ApprovalDecisionRecorded",
                "model-hub",
                "model_provider",
                "3001",
                "REJECTED",
                "REJECT",
                occurredAt,
                "缺少连通性证据"
        ));

        verify(service).applyProviderDecision(
                TenantId.of("2001"), "3001", "REQ-9001", "model-approval-evidence/v1",
                "a".repeat(64), "b".repeat(64), false, occurredAt
        );
    }

    @Test
    void receiptsBrokerDelayedV1WithoutMutatingModelState() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        when(mapper.resolveLegacyApprovalRequestId(
                2001L, 9001L, "model-hub", "model_provider", 3001L
        )).thenReturn("REQ-9001");
        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(1);
        ApprovalDecisionRecordedConsumer consumer = new ApprovalDecisionRecordedConsumer(
                new ApprovalDecisionInboxService(mapper, service),
                new JsonMapper()
        );
        Instant occurredAt = Instant.parse("2026-05-25T08:00:00Z");

        consumer.onMessage(legacyMessage(occurredAt));

        verifyNoInteractions(service);
    }

    @Test
    void receiptsBrokerDelayedV2WithoutMutatingModelState() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(1);
        ApprovalDecisionRecordedConsumer consumer = new ApprovalDecisionRecordedConsumer(
                new ApprovalDecisionInboxService(mapper, service),
                new JsonMapper()
        );
        DomainEventMessage current = message(
                "ApprovalDecisionRecorded",
                "model-hub",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                Instant.parse("2026-05-25T08:00:00Z")
        );

        consumer.onMessage(copyEnvelope(
                current,
                "approval-center",
                "ApprovalTask",
                "9001",
                "v2"
        ));

        verify(mapper).insertReceipt(any(ApprovalDecisionInboxReceipt.class));
        verifyNoInteractions(service);
    }

    @Test
    void ignoresApprovalDecisionForOtherSourceContext() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);

        consumer.onMessage(message(
                "ApprovalDecisionRecorded",
                "knowledge-base",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                Instant.parse("2026-05-25T08:00:00Z")
        ));

        verifyNoInteractions(service);
    }

    @Test
    void ignoresOtherDomainEventsOnSharedTopic() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);

        consumer.onMessage(message(
                "ModelConfigApprovalRequested",
                "model-hub",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                Instant.parse("2026-05-25T08:00:00Z")
        ));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnsupportedDecisionStatusInsteadOfDisablingProvider() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);

        assertThatThrownBy(() -> consumer.onMessage(message(
                "ApprovalDecisionRecorded",
                "model-hub",
                "model_provider",
                "3001",
                "CANCELLED",
                "APPROVE",
                Instant.parse("2026-05-25T08:00:00Z")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批决策事件载荷解析失败")
                .hasRootCauseMessage("审批状态不支持：CANCELLED");

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUntrustedOrMismatchedDecisionEnvelopesBeforeInboxMutation() {
        ModelApprovalDecisionApplicationService service = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionRecordedConsumer consumer = consumer(service);
        DomainEventMessage valid = message(
                "ApprovalDecisionRecorded",
                "model-hub",
                "model_provider",
                "3001",
                "APPROVED",
                "APPROVE",
                Instant.parse("2026-05-25T08:00:00Z")
        );

        assertEnvelopeRejected(consumer, copyEnvelope(valid, "untrusted", "ApprovalTask", "9001", "v3"));
        assertEnvelopeRejected(consumer, copyEnvelope(valid, "approval-center", "ModelProvider", "9001", "v3"));
        assertEnvelopeRejected(consumer, copyEnvelope(valid, "approval-center", "ApprovalTask", "9999", "v3"));
        assertThatThrownBy(() -> consumer.onMessage(copyEnvelope(
                valid, "approval-center", "ApprovalTask", "9001", "v4"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion 不支持");

        verifyNoInteractions(service);
    }

    private static DomainEventMessage message(
            String eventType,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String status,
            String action,
            Instant occurredAt
    ) {
        return message(eventType, sourceContext, sourceResourceType, sourceResourceId, status, action, occurredAt, null);
    }

    private static DomainEventMessage message(
            String eventType,
            String sourceContext,
            String sourceResourceType,
            String sourceResourceId,
            String status,
            String action,
            Instant occurredAt,
            String comment
    ) {
        String commentJson = comment == null ? "null" : "\"" + comment + "\"";
        return new DomainEventMessage(
                "ApprovalDecisionRecorded:2001:9001:" + status,
                eventType,
                "2001",
                "ApprovalTask",
                "9001",
                1,
                "v3",
                occurredAt,
                "TRACE-APPROVAL-001",
                "approval-center",
                """
                        {
                          "taskId": "9001",
                          "approvalRequestId": "REQ-9001",
                          "sourceContext": "%s",
                          "sourceResourceType": "%s",
                          "sourceResourceId": "%s",
                          "status": "%s",
                          "action": "%s",
                          "approverId": "1001",
                          "comment": %s,
                          "evidenceSchema": "model-approval-evidence/v1",
                          "evidenceDigest": "%s",
                          "targetDigest": "%s"
                        }
                        """.formatted(
                                sourceContext, sourceResourceType, sourceResourceId, status, action, commentJson,
                                "a".repeat(64), "b".repeat(64)
                        )
        );
    }

    private static ApprovalDecisionRecordedConsumer consumer(ModelApprovalDecisionApplicationService service) {
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(1);
        return new ApprovalDecisionRecordedConsumer(
                new ApprovalDecisionInboxService(mapper, service),
                new JsonMapper()
        );
    }

    private static DomainEventMessage legacyMessage(Instant occurredAt) {
        return new DomainEventMessage(
                "ApprovalDecisionRecorded:2001:9001:APPROVED",
                "ApprovalDecisionRecorded",
                "2001",
                "ApprovalTask",
                "9001",
                1,
                "v1",
                occurredAt,
                "TRACE-APPROVAL-V1-001",
                "approval-center",
                """
                        {
                          "taskId": "9001",
                          "sourceContext": "model-hub",
                          "sourceResourceType": "model_provider",
                          "sourceResourceId": "3001",
                          "status": "APPROVED",
                          "action": "APPROVE",
                          "approverId": "1001",
                          "comment": null
                        }
                        """
        );
    }

    private static void assertEnvelopeRejected(
            ApprovalDecisionRecordedConsumer consumer,
            DomainEventMessage message
    ) {
        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批决策事件 envelope 与载荷不一致");
    }

    private static DomainEventMessage copyEnvelope(
            DomainEventMessage source,
            String producer,
            String aggregateType,
            String aggregateId,
            String schemaVersion
    ) {
        return new DomainEventMessage(
                source.messageId(),
                source.eventType(),
                source.tenantId(),
                aggregateType,
                aggregateId,
                source.version(),
                schemaVersion,
                source.occurredAt(),
                source.traceId(),
                producer,
                source.payloadJson()
        );
    }
}
