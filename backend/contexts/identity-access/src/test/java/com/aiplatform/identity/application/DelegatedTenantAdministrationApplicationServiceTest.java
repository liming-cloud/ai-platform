package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.AssignIamTenantAdministratorCommand;
import com.aiplatform.identity.application.command.ExecuteDelegatedTenantActionCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.port.IamConfigurationCommandPort;
import com.aiplatform.identity.application.result.DelegatedTenantActionResult;
import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionRequest;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionType;
import com.aiplatform.identity.domain.port.DelegatedTenantActionRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DelegatedTenantAdministrationApplicationServiceTest {
    @Test
    void replacesTenantRolePermissionsAndPersistsAuditedDelegation() {
        IamConfigurationCommandPort commands = mock(IamConfigurationCommandPort.class);
        DelegatedTenantActionRepository actions = mock(DelegatedTenantActionRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(actions.findByRequestHash(any(), any(), any())).thenReturn(Optional.empty());
        when(actions.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0, DelegatedTenantAction.class).withActionId("8101"));
        DelegatedTenantAdministrationApplicationService service = service(commands, actions, auditPublisher);

        DelegatedTenantActionResult result = service.execute(command(
                "REPLACE_ROLE_PERMISSIONS",
                "3002",
                null,
                List.of("knowledge-base:knowledge-base:read"),
                "应急修复错误授权",
                "request-001"
        ));

        assertThat(result.delegationActionId()).isEqualTo("8101");
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        ArgumentCaptor<ReplaceIamRolePermissionsCommand> mutation =
                ArgumentCaptor.forClass(ReplaceIamRolePermissionsCommand.class);
        verify(commands).replaceRolePermissions(mutation.capture());
        assertThat(mutation.getValue().tenantId()).isEqualTo(TenantId.of("9001"));
        assertThat(mutation.getValue().actorId()).isEqualTo("1001");
        assertThat(mutation.getValue().permissionCodes())
                .containsExactly("knowledge-base:knowledge-base:read");

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(audit.capture());
        assertThat(audit.getValue().tenantId()).isEqualTo("9001");
        assertThat(audit.getValue().action()).isEqualTo("IAM_TENANT_DELEGATION_EXECUTED");
        assertThat(audit.getValue().detailAttributes())
                .containsEntry("reason", "应急修复错误授权")
                .containsEntry("operatorTenantId", "2001")
                .containsEntry("delegationActionId", "8101");
        assertThat(result.auditEventId()).isEqualTo(audit.getValue().eventId());
    }

    @Test
    void assignsExistingTenantUserToTenantAdminRole() {
        IamConfigurationCommandPort commands = mock(IamConfigurationCommandPort.class);
        DelegatedTenantActionRepository actions = mock(DelegatedTenantActionRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(actions.findByRequestHash(any(), any(), any())).thenReturn(Optional.empty());
        when(actions.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0, DelegatedTenantAction.class).withActionId("8102"));
        DelegatedTenantAdministrationApplicationService service = service(commands, actions, auditPublisher);

        service.execute(command(
                "ASSIGN_TENANT_ADMIN",
                "3002",
                "1008",
                List.of(),
                "租户初始化补充管理员",
                "request-002"
        ));

        verify(commands).assignTenantAdministrator(new AssignIamTenantAdministratorCommand(
                TenantId.of("9001"),
                "1001",
                "TRACE-DELEGATION-001",
                "1008",
                "3002"
        ));
        verify(commands, never()).replaceRolePermissions(any());
    }

    @Test
    void returnsExistingResultWithoutRepeatingMutationOrAudit() {
        IamConfigurationCommandPort commands = mock(IamConfigurationCommandPort.class);
        DelegatedTenantActionRepository actions = mock(DelegatedTenantActionRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        DelegatedTenantAction existing = existingAction();
        when(actions.findByRequestHash(any(), any(), any())).thenReturn(Optional.of(existing));
        DelegatedTenantAdministrationApplicationService service = service(commands, actions, auditPublisher);

        DelegatedTenantActionResult result = service.execute(command(
                "REPLACE_ROLE_PERMISSIONS",
                "3002",
                null,
                List.of("knowledge-base:knowledge-base:read"),
                "应急修复错误授权",
                "request-001"
        ));

        assertThat(result.delegationActionId()).isEqualTo("8101");
        verifyNoInteractions(commands, auditPublisher);
        verify(actions, never()).save(any());
    }

    @Test
    void concurrentIdempotencyConflictDoesNotRepeatMutationOrAudit() {
        IamConfigurationCommandPort commands = mock(IamConfigurationCommandPort.class);
        DelegatedTenantActionRepository actions = mock(DelegatedTenantActionRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(actions.findByRequestHash(any(), any(), any())).thenReturn(Optional.empty());
        when(actions.save(any())).thenReturn(existingAction());
        DelegatedTenantAdministrationApplicationService service = service(commands, actions, auditPublisher);

        DelegatedTenantActionResult result = service.execute(command(
                "REPLACE_ROLE_PERMISSIONS",
                "3002",
                null,
                List.of("knowledge-base:knowledge-base:read"),
                "应急修复错误授权",
                "request-001"
        ));

        assertThat(result.delegationActionId()).isEqualTo("8101");
        verifyNoInteractions(commands, auditPublisher);
    }

    private static DelegatedTenantAdministrationApplicationService service(
            IamConfigurationCommandPort commands,
            DelegatedTenantActionRepository actions,
            AuditEventPublisher auditPublisher
    ) {
        return new DelegatedTenantAdministrationApplicationService(
                commands,
                actions,
                auditPublisher,
                Clock.fixed(Instant.parse("2026-07-10T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static ExecuteDelegatedTenantActionCommand command(
            String actionType,
            String roleId,
            String userId,
            List<String> permissionCodes,
            String reason,
            String idempotencyKey
    ) {
        return new ExecuteDelegatedTenantActionCommand(
                TenantId.of("2001"),
                TenantId.of("9001"),
                "1001",
                "TRACE-DELEGATION-001",
                actionType,
                roleId,
                userId,
                permissionCodes,
                reason,
                idempotencyKey
        );
    }

    private static DelegatedTenantAction existingAction() {
        DelegatedTenantActionRequest request = new DelegatedTenantActionRequest(
                DelegatedTenantActionType.REPLACE_ROLE_PERMISSIONS,
                "3002",
                null,
                List.of("knowledge-base:knowledge-base:read"),
                "应急修复错误授权",
                "request-001"
        );
        return DelegatedTenantAction.succeeded(
                TenantId.of("9001"),
                "1001",
                request,
                "hash",
                "AUDIT-001",
                Instant.parse("2026-07-10T03:00:00Z")
        ).withActionId("8101");
    }
}
