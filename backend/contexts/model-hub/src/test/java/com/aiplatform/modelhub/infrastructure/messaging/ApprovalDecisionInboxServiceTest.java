package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.application.ModelApprovalDecisionApplicationService;
import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxReceipt;
import com.aiplatform.modelhub.infrastructure.messaging.inbox.ApprovalDecisionInboxService;
import com.aiplatform.modelhub.infrastructure.messaging.model.ApprovalDecisionPayload;
import com.aiplatform.modelhub.infrastructure.messaging.model.LegacyApprovalDecisionPayload;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ApprovalDecisionInboxMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ApprovalDecisionInboxServiceTest {
    @Test
    void duplicateDeliveryMutatesProviderAndItsUsagePolicyOutboxOnlyOnce() {
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        ModelApprovalDecisionApplicationService decisions = mock(ModelApprovalDecisionApplicationService.class);
        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(1, 0);
        ApprovalDecisionInboxService inbox = new ApprovalDecisionInboxService(mapper, decisions);
        DomainEventMessage message = message("model_provider", "3001", "APPROVED");
        ApprovalDecisionPayload payload = payload("model_provider", "3001", "APPROVED");

        assertThat(inbox.process(message, payload)).isTrue();
        assertThat(inbox.process(message, payload)).isFalse();

        verify(mapper, times(2)).insertReceipt(any(ApprovalDecisionInboxReceipt.class));
        verify(decisions).applyProviderDecision(
                TenantId.of("2001"), "3001", "REQ-9001", "model-approval-evidence/v1",
                "a".repeat(64), "b".repeat(64), true, message.occurredAt()
        );
        verifyNoMoreInteractions(decisions);
    }

    @Test
    void receiptIsPersistedBeforeAConfigDecisionAndInvalidInsertCountsFailClosed() {
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        ModelApprovalDecisionApplicationService decisions = mock(ModelApprovalDecisionApplicationService.class);
        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(1);
        ApprovalDecisionInboxService inbox = new ApprovalDecisionInboxService(mapper, decisions);
        DomainEventMessage message = message("model_config", "4001", "REJECTED");

        assertThat(inbox.process(message, payload("model_config", "4001", "REJECTED"))).isTrue();

        InOrder order = inOrder(mapper, decisions);
        order.verify(mapper).insertReceipt(any(ApprovalDecisionInboxReceipt.class));
        order.verify(decisions).applyModelConfigDecision(
                TenantId.of("2001"), "4001", "REQ-9001", "model-approval-evidence/v1",
                "a".repeat(64), "b".repeat(64), false, message.occurredAt()
        );

        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(2);
        assertThatThrownBy(() -> inbox.process(message, payload("model_config", "4001", "REJECTED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审批决策 inbox 写入结果不合法");
    }

    @Test
    void legacyDecisionUsesOnlyTheExactTenantTaskAndResourceCorrelation() {
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        ModelApprovalDecisionApplicationService decisions = mock(ModelApprovalDecisionApplicationService.class);
        when(mapper.resolveLegacyApprovalRequestId(
                2001L, 9001L, "model-hub", "model_provider", 3001L
        )).thenReturn("REQ-9001");
        when(mapper.insertReceipt(any(ApprovalDecisionInboxReceipt.class))).thenReturn(1);
        ApprovalDecisionInboxService inbox = new ApprovalDecisionInboxService(mapper, decisions);
        DomainEventMessage message = message("model_provider", "3001", "APPROVED");

        assertThat(inbox.processLegacy(message, legacyPayload("model_provider", "3001", "APPROVED"))).isTrue();

        verify(mapper).resolveLegacyApprovalRequestId(2001L, 9001L, "model-hub", "model_provider", 3001L);
        verifyNoMoreInteractions(decisions);
    }

    @Test
    void mismatchedLegacyFactsFailClosedBeforeReceiptOrDecision() {
        ApprovalDecisionInboxMapper mapper = mock(ApprovalDecisionInboxMapper.class);
        ModelApprovalDecisionApplicationService decisions = mock(ModelApprovalDecisionApplicationService.class);
        ApprovalDecisionInboxService inbox = new ApprovalDecisionInboxService(mapper, decisions);
        DomainEventMessage message = message("model_provider", "3999", "APPROVED");

        assertThat(inbox.processLegacy(message, legacyPayload("model_provider", "3999", "APPROVED"))).isFalse();

        verify(mapper).resolveLegacyApprovalRequestId(2001L, 9001L, "model-hub", "model_provider", 3999L);
        verifyNoMoreInteractions(mapper);
        verifyNoMoreInteractions(decisions);
    }

    private static DomainEventMessage message(String resourceType, String resourceId, String status) {
        return new DomainEventMessage(
                "ApprovalDecisionRecorded:2001:9001:" + status,
                "ApprovalDecisionRecorded",
                "2001",
                "ApprovalTask",
                "9001",
                1,
                "v1",
                Instant.parse("2026-07-18T08:00:00Z"),
                "trace-approval-9001",
                "approval-center",
                "{\"sourceResourceType\":\"" + resourceType + "\",\"sourceResourceId\":\"" + resourceId + "\"}"
        );
    }

    private static ApprovalDecisionPayload payload(String resourceType, String resourceId, String status) {
        boolean approved = "APPROVED".equals(status);
        return new ApprovalDecisionPayload(
                "9001",
                "REQ-9001",
                "model-hub",
                resourceType,
                resourceId,
                status,
                approved ? "APPROVE" : "REJECT",
                "1002",
                approved ? null : "风险未关闭",
                "model-approval-evidence/v1",
                "a".repeat(64),
                "b".repeat(64)
        );
    }

    private static LegacyApprovalDecisionPayload legacyPayload(String resourceType, String resourceId, String status) {
        boolean approved = "APPROVED".equals(status);
        return new LegacyApprovalDecisionPayload(
                "9001",
                "model-hub",
                resourceType,
                resourceId,
                status,
                approved ? "APPROVE" : "REJECT",
                "1002",
                approved ? null : "风险未关闭"
        );
    }
}
