package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.ChangeIamTenantStatusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.application.command.UpdateIamMenuCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.application.port.IamConfigurationCommandPort;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

class IamConfigurationCommandApplicationServiceTest {
    @Test
    void iamConfigurationCommandApplicationServiceIsTransactionalSpringService() throws NoSuchMethodException {
        assertThat(IamConfigurationCommandApplicationService.class.getAnnotation(Service.class)).isNotNull();
        assertThat(IamConfigurationCommandApplicationService.class
                .getMethod("updateMenu", UpdateIamMenuCommand.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void createsMenuThroughPortAndPublishesIamAuditEvent() {
        IamConfigurationCommandPort port = mock(IamConfigurationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        IamConfigurationCommandApplicationService service = new IamConfigurationCommandApplicationService(
                port,
                auditPublisher,
                fixedClock()
        );
        CreateIamMenuCommand command = new CreateIamMenuCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-007",
                "admin.audit-log",
                "审计日志",
                "ADMIN",
                "/admin/audit-log",
                "AdminAuditLog",
                "admin/audit-log",
                "audit",
                "identity-access:iam-menu:manage",
                60,
                "ACTIVE",
                List.of("identity-settings")
        );
        when(port.createMenu(command)).thenReturn("5010");

        String menuId = service.createMenu(command);

        assertThat(menuId).isEqualTo("5010");
        verify(port).createMenu(command);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo("2001");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(eventCaptor.getValue().resourceType()).isEqualTo("IAM_MENU");
        assertThat(eventCaptor.getValue().resourceId()).isEqualTo("5010");
        assertThat(eventCaptor.getValue().action()).isEqualTo("IAM_MENU_CREATE");
        assertThat(eventCaptor.getValue().riskLevel()).isEqualTo("MEDIUM");
        assertThat(eventCaptor.getValue().traceId()).isEqualTo("TRACE-IAM-SAVE-007");
        assertThat(eventCaptor.getValue().createdAt()).isEqualTo(Instant.parse("2026-06-24T07:40:00Z"));
    }

    @Test
    void updatesMenuThroughPortAndPublishesIamAuditEvent() {
        IamConfigurationCommandPort port = mock(IamConfigurationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        IamConfigurationCommandApplicationService service = new IamConfigurationCommandApplicationService(
                port,
                auditPublisher,
                fixedClock()
        );
        UpdateIamMenuCommand command = new UpdateIamMenuCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-001",
                "5009",
                "系统设置",
                "/admin/system-settings",
                "AdminSystemSettings",
                "admin/system-settings",
                "settings",
                "identity-access:system-setting:manage",
                30,
                "ACTIVE"
        );

        service.updateMenu(command);

        verify(port).updateMenu(command);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo("2001");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(eventCaptor.getValue().resourceType()).isEqualTo("IAM_MENU");
        assertThat(eventCaptor.getValue().resourceId()).isEqualTo("5009");
        assertThat(eventCaptor.getValue().action()).isEqualTo("IAM_MENU_UPDATE");
        assertThat(eventCaptor.getValue().riskLevel()).isEqualTo("MEDIUM");
        assertThat(eventCaptor.getValue().traceId()).isEqualTo("TRACE-IAM-SAVE-001");
        assertThat(eventCaptor.getValue().createdAt()).isEqualTo(Instant.parse("2026-06-24T07:40:00Z"));
    }

    @Test
    void roleMenuReplacementPublishesHighRiskAuditEvent() {
        IamConfigurationCommandPort port = mock(IamConfigurationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        IamConfigurationCommandApplicationService service = new IamConfigurationCommandApplicationService(
                port,
                auditPublisher,
                fixedClock()
        );
        ReplaceIamRoleMenusCommand command = new ReplaceIamRoleMenusCommand(
                TenantId.of("2001"),
                "1002",
                "TRACE-IAM-SAVE-002",
                "3002",
                List.of("5002", "5003")
        );

        service.replaceRoleMenus(command);

        verify(port).replaceRoleMenus(command);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().resourceType()).isEqualTo("IAM_ROLE_MENU");
        assertThat(eventCaptor.getValue().action()).isEqualTo("IAM_ROLE_MENU_UPDATE");
        assertThat(eventCaptor.getValue().riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void tenantFeatureUpdatePublishesFeatureCodeInAuditDetails() {
        IamConfigurationCommandPort port = mock(IamConfigurationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        IamConfigurationCommandApplicationService service = new IamConfigurationCommandApplicationService(
                port,
                auditPublisher,
                fixedClock()
        );
        UpdateIamTenantFeatureCommand command = new UpdateIamTenantFeatureCommand(
                TenantId.of("2001"),
                "1001",
                "TRACE-IAM-SAVE-003",
                "identity-settings",
                true,
                "租户已完成能力开通评审"
        );

        service.updateTenantFeature(command);

        verify(port).updateTenantFeature(command);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().resourceType()).isEqualTo("IAM_TENANT_FEATURE");
        assertThat(eventCaptor.getValue().resourceId()).isNull();
        assertThat(eventCaptor.getValue().action()).isEqualTo("IAM_TENANT_FEATURE_UPDATE");
        assertThat(eventCaptor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(eventCaptor.getValue().detailAttributes())
                .containsEntry("featureCode", "identity-settings")
                .containsEntry("enabled", true)
                .containsEntry("reason", "租户已完成能力开通评审");
    }

    @Test
    void rolePermissionReplacementPublishesFrozenSetAndReasonInAuditDetails() {
        IamConfigurationCommandPort port = mock(IamConfigurationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        IamConfigurationCommandApplicationService service = new IamConfigurationCommandApplicationService(
                port, auditPublisher, fixedClock()
        );
        ReplaceIamRolePermissionsCommand command = new ReplaceIamRolePermissionsCommand(
                TenantId.of("2001"), "1001", "TRACE-IAM-SAVE-004", "3002",
                List.of("app-studio:application:use", "knowledge-base:retrieval:query"),
                "按岗位职责替换完整权限集合"
        );

        service.replaceRolePermissions(command);

        verify(port).replaceRolePermissions(command);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("IAM_ROLE_PERMISSION_UPDATE");
        assertThat(eventCaptor.getValue().detailAttributes())
                .containsEntry("permissionCodes", command.permissionCodes())
                .containsEntry("reason", "按岗位职责替换完整权限集合");
    }

    @Test
    void tenantStatusChangePublishesNewStatusAndReasonInAuditDetails() {
        IamConfigurationCommandPort port = mock(IamConfigurationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        IamConfigurationCommandApplicationService service = new IamConfigurationCommandApplicationService(
                port, auditPublisher, fixedClock()
        );
        ChangeIamTenantStatusCommand command = new ChangeIamTenantStatusCommand(
                TenantId.of("2001"), "1001", "TRACE-IAM-STATUS-001", "DISABLED", "合同到期停服"
        );

        service.changeTenantStatus(command);

        verify(port).changeTenantStatus(command);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("IAM_TENANT_STATUS_CHANGE");
        assertThat(eventCaptor.getValue().detailAttributes())
                .containsEntry("status", "DISABLED")
                .containsEntry("reason", "合同到期停服");
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-24T07:40:00Z"), ZoneOffset.UTC);
    }
}
