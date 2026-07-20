package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.port.PermissionRequestRepository;
import com.aiplatform.identity.application.error.IdentityErrorCodes;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestQuery;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestSensitiveTextPolicy;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestStatus;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class PermissionRequestApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");
    private static final String TRACE_ID = "TRACE-PERMISSION-001";

    @Test
    void approvesOnlyCanonicalRequestThenPersistsAclAndSecretFreeAuditInOrder() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        PermissionRequest pending = pending(
                TENANT_ID,
                "request-1",
                "历史原因 password=legacy-secret"
        );
        when(repository.findById(TENANT_ID, pending.requestId())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PermissionRequestApplicationService service = service(repository, acl, audits);

        PermissionRequest decided = service.decide(
                TENANT_ID,
                "1002",
                pending.requestId(),
                "APPROVE",
                "需求合理，请在应用授权页完成授权",
                TRACE_ID
        );

        assertThat(decided.status()).isEqualTo(PermissionRequestStatus.APPROVED);
        assertThat(decided.reason()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        InOrder order = inOrder(repository, acl, audits);
        order.verify(repository).findById(TENANT_ID, pending.requestId());
        ArgumentCaptor<PermissionRequest> saved = ArgumentCaptor.forClass(PermissionRequest.class);
        order.verify(repository).save(saved.capture());
        assertThat(saved.getValue().reason()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        order.verify(acl).grantAllowingSubject(any(), any(), any(), any(), any(), any());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        order.verify(audits).publish(audit.capture());

        assertThat(audit.getValue().tenantId()).isEqualTo(TENANT_ID.value());
        assertThat(audit.getValue().actorId()).isEqualTo("1002");
        assertThat(audit.getValue().traceId()).isEqualTo(TRACE_ID);
        assertThat(audit.getValue().resourceType()).isEqualTo("IAM_PERMISSION_REQUEST");
        assertThat(audit.getValue().resourceId()).isEqualTo("request-1");
        assertThat(audit.getValue().action()).isEqualTo("PERMISSION_REQUEST_DECIDED");
        assertThat(audit.getValue().detailAttributes())
                .containsEntry("permissionRequestId", "request-1")
                .containsEntry("decision", "APPROVED")
                .containsEntry("permissionCode", "app-studio:application:use")
                .containsEntry("targetResourceType", "AI_APPLICATION")
                .containsEntry("targetResourceId", "3001")
                .doesNotContainKeys("reason", "comment", "decisionComment");
        assertThat(audit.getValue().detailAttributes().toString()).doesNotContain("legacy-secret");
    }

    @Test
    void rejectionPersistsAndAuditsWithoutGrantingAcl() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        PermissionRequest pending = pending(TENANT_ID, "request-1", "合法原因");
        when(repository.findById(TENANT_ID, pending.requestId())).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PermissionRequestApplicationService service = service(repository, acl, audits);

        PermissionRequest decided = service.decide(
                TENANT_ID, "1002", pending.requestId(), "REJECT", "当前职责不需要该权限", TRACE_ID);

        assertThat(decided.status()).isEqualTo(PermissionRequestStatus.REJECTED);
        verifyNoInteractions(acl);
        verify(audits).publish(any(AuditEvent.class));
    }

    @Test
    void rejectsRequesterSelfDecisionBeforeAnyStateAclOrAuditMutation() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        PermissionRequest pending = pending(TENANT_ID, "request-1", "合法原因");
        when(repository.findById(TENANT_ID, pending.requestId())).thenReturn(Optional.of(pending));
        PermissionRequestApplicationService service = service(repository, acl, audits);

        for (String action : List.of("APPROVE", "REJECT")) {
            ApplicationException exception = assertThrows(ApplicationException.class, () -> service.decide(
                    TENANT_ID, pending.requesterId(), pending.requestId(), action, "自行处理", TRACE_ID
            ));

            assertThat(exception.errorCode()).isEqualTo(IdentityErrorCodes.PERMISSION_REQUEST_DECISION_DENIED);
            assertThat(exception.detail()).isEqualTo("申请人不能处理自己的权限申请");
        }
        verify(repository, never()).save(any());
        verifyNoInteractions(acl, audits);
    }

    @Test
    void rejectsSecretBearingNewReasonAndCommentBeforePersistenceOrMutation() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        PermissionRequestApplicationService service = service(repository, acl, audits);

        assertThatThrownBy(() -> service.create(
                TENANT_ID,
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                "Authorization: Bearer super-secret-token"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("申请原因不能包含疑似认证凭据或秘密");

        assertThatThrownBy(() -> service.decide(
                TENANT_ID,
                "1002",
                "request-1",
                "REJECT",
                "api_key=super-secret-value",
                TRACE_ID
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处理意见不能包含疑似认证凭据或秘密");

        verifyNoInteractions(repository, acl, audits);
    }

    @Test
    void rejectsCrossTenantOrWrongRequestIdentityBeforeAnyMutation() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(repository.findById(TENANT_ID, "request-1"))
                .thenReturn(Optional.of(pending(TenantId.of("9001"), "request-1", "合法原因")));
        when(repository.findById(TENANT_ID, "request-2"))
                .thenReturn(Optional.of(pending(TENANT_ID, "request-other", "合法原因")));
        PermissionRequestApplicationService service = service(repository, acl, audits);

        assertThatThrownBy(() -> service.decide(
                TENANT_ID, "1002", "request-1", "APPROVE", "批准", TRACE_ID
        )).isInstanceOf(ApplicationException.class).hasMessageContaining("权限申请不可见");
        assertThatThrownBy(() -> service.decide(
                TENANT_ID, "1002", "request-2", "APPROVE", "批准", TRACE_ID
        )).isInstanceOf(ApplicationException.class).hasMessageContaining("权限申请不可见");

        verify(repository, never()).save(any());
        verifyNoInteractions(acl, audits);
    }

    @Test
    void rejectsWrongCanonicalIdentityReturnedBySaveBeforeAclOrAudit() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        PermissionRequest pending = pending(TENANT_ID, "request-1", "合法原因");
        when(repository.findById(TENANT_ID, "request-1")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenReturn(decided(TENANT_ID, "request-other"));
        PermissionRequestApplicationService service = service(repository, acl, audits);

        assertThatThrownBy(() -> service.decide(
                TENANT_ID, "1002", "request-1", "APPROVE", "批准", TRACE_ID
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("权限申请保存结果身份不一致");

        verifyNoInteractions(acl, audits);
    }

    @Test
    void saveFailurePreventsAclAndAuditAndAuditFailureIsPropagatedForRollback() {
        PermissionRequestRepository failedRepository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort acl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        PermissionRequest pending = pending(TENANT_ID, "request-1", "合法原因");
        when(failedRepository.findById(TENANT_ID, "request-1")).thenReturn(Optional.of(pending));
        when(failedRepository.save(any())).thenThrow(new IllegalStateException("decision persistence failed"));

        assertThatThrownBy(() -> service(failedRepository, acl, audits).decide(
                TENANT_ID, "1002", "request-1", "APPROVE", "批准", TRACE_ID
        )).isInstanceOf(IllegalStateException.class).hasMessage("decision persistence failed");
        verifyNoInteractions(acl, audits);

        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        ResourceAclCommandPort secondAcl = mock(ResourceAclCommandPort.class);
        AuditEventPublisher failingAudit = mock(AuditEventPublisher.class);
        when(repository.findById(TENANT_ID, "request-1")).thenReturn(Optional.of(pending));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("audit persistence failed")).when(failingAudit).publish(any());

        assertThatThrownBy(() -> service(repository, secondAcl, failingAudit).decide(
                TENANT_ID, "1002", "request-1", "APPROVE", "批准", TRACE_ID
        )).isInstanceOf(IllegalStateException.class).hasMessage("audit persistence failed");
        verify(secondAcl).grantAllowingSubject(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listQuarantinesLegacyTextAndFailsClosedForForeignRows() {
        PermissionRequestRepository repository = mock(PermissionRequestRepository.class);
        when(repository.findPage(any(PermissionRequestQuery.class))).thenReturn(new PermissionRequestPage(
                List.of(pending(TENANT_ID, "request-1", "历史 token=legacy-secret")), 1, 1, 20
        ));
        PermissionRequestApplicationService service = service(
                repository,
                mock(ResourceAclCommandPort.class),
                mock(AuditEventPublisher.class)
        );

        assertThat(service.list(TENANT_ID, "pending", 1, 20).items()).singleElement()
                .extracting(PermissionRequest::reason)
                .isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);

        when(repository.findPage(any(PermissionRequestQuery.class))).thenReturn(new PermissionRequestPage(
                List.of(pending(TenantId.of("9001"), "request-2", "合法原因")), 1, 1, 20
        ));
        assertThatThrownBy(() -> service.list(TENANT_ID, "PENDING", 1, 20))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("权限申请不可见");

        assertThatThrownBy(() -> service.list(TENANT_ID, "UNKNOWN", 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的权限申请状态：UNKNOWN");
    }

    private static PermissionRequestApplicationService service(
            PermissionRequestRepository repository,
            ResourceAclCommandPort acl,
            AuditEventPublisher audits
    ) {
        return new PermissionRequestApplicationService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                acl,
                audits
        );
    }

    private static PermissionRequest pending(TenantId tenantId, String requestId, String reason) {
        return new PermissionRequest(
                tenantId,
                requestId,
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                reason,
                PermissionRequestStatus.PENDING,
                null,
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)
        );
    }

    private static PermissionRequest decided(TenantId tenantId, String requestId) {
        return new PermissionRequest(
                tenantId,
                requestId,
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                "合法原因",
                PermissionRequestStatus.APPROVED,
                "1002",
                "批准",
                NOW.minusSeconds(60),
                NOW
        );
    }
}
