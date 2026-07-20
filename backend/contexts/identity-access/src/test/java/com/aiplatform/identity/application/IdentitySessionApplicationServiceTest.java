package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.CurrentUserCommand;
import com.aiplatform.identity.application.command.LogoutCommand;
import com.aiplatform.identity.application.port.ConsoleMenuQueryPort;
import com.aiplatform.identity.application.result.AuthorizedConsoleMenuSnapshot;
import com.aiplatform.identity.application.result.CurrentUserMenuResult;
import com.aiplatform.identity.application.result.CurrentUserResult;
import com.aiplatform.identity.domain.AccessToken;
import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.identity.domain.port.EnterpriseUserRepository;
import com.aiplatform.identity.domain.port.LoginSessionRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.stereotype.Service;

class IdentitySessionApplicationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-22T02:30:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void identitySessionApplicationServiceIsSpringServiceBean() {
        assertThat(IdentitySessionApplicationService.class.getAnnotation(Service.class)).isNotNull();
    }

    @Test
    void currentUserRestoresActiveSessionAndLoadsUserProfile() {
        LoginSessionRepository sessions = mock(LoginSessionRepository.class);
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        RbacAuthorizationQueryPort authorizations = mock(RbacAuthorizationQueryPort.class);
        ConsoleMenuQueryPort menuQuery = mock(ConsoleMenuQueryPort.class);
        when(sessions.findActiveByToken(TenantId.of("2001"), "token-1001")).thenReturn(Optional.of(activeSession()));
        when(users.findById(TenantId.of("2001"), "1001")).thenReturn(Optional.of(activeUser()));
        Set<PermissionCode> permissionCodes = Set.of(
                new PermissionCode("model-hub:provider:read"),
                new PermissionCode("app-studio:application:read")
        );
        when(authorizations.findPermissionCodes(TenantId.of("2001"), "user", "1001"))
                .thenReturn(permissionCodes);
        when(menuQuery.findAuthorizedMenus(
                TenantId.of("2001"),
                "user",
                "1001",
                Set.of("platform_admin", "auditor"),
                permissionCodes
        )).thenReturn(new AuthorizedConsoleMenuSnapshot(List.of(validMenu()), "/admin/model-providers"));
        IdentitySessionApplicationService service = new IdentitySessionApplicationService(
                sessions,
                users,
                audits,
                authorizations,
                menuQuery,
                FIXED_CLOCK
        );

        CurrentUserResult result = service.currentUser(new CurrentUserCommand(
                TenantId.of("2001"),
                "token-1001",
                "TRACE-ME-001"
        ));

        assertThat(result.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(result.userId()).isEqualTo("1001");
        assertThat(result.loginName()).isEqualTo("admin");
        assertThat(result.displayName()).isEqualTo("平台管理员");
        assertThat(result.roleCodes()).containsExactlyInAnyOrder("platform_admin", "auditor");
        assertThat(result.permissionCodes())
                .extracting(PermissionCode::value)
                .containsExactlyInAnyOrder("model-hub:provider:read", "app-studio:application:read");
        assertThat(result.menus()).extracting(CurrentUserMenuResult::code).containsExactly("admin.model-providers");
        assertThat(result.defaultRoute()).isEqualTo("/admin/model-providers");
        assertThat(result.accessTokenExpiresAt()).isEqualTo(Instant.parse("2026-05-22T03:30:00Z"));
        verify(sessions).findActiveByToken(TenantId.of("2001"), "token-1001");
        verify(users).findById(TenantId.of("2001"), "1001");
        verify(authorizations).findPermissionCodes(TenantId.of("2001"), "user", "1001");
        verify(menuQuery).findAuthorizedMenus(
                TenantId.of("2001"),
                "user",
                "1001",
                Set.of("platform_admin", "auditor"),
                permissionCodes
        );
        verifyNoInteractions(audits);
    }

    @Test
    void currentUserRejectsMissingOrExpiredSession() {
        LoginSessionRepository sessions = mock(LoginSessionRepository.class);
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        RbacAuthorizationQueryPort authorizations = mock(RbacAuthorizationQueryPort.class);
        ConsoleMenuQueryPort menuQuery = mock(ConsoleMenuQueryPort.class);
        when(sessions.findActiveByToken(TenantId.of("2001"), "missing-token")).thenReturn(Optional.empty());
        IdentitySessionApplicationService service = new IdentitySessionApplicationService(
                sessions,
                users,
                audits,
                authorizations,
                menuQuery,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.currentUser(new CurrentUserCommand(
                TenantId.of("2001"),
                "missing-token",
                "TRACE-ME-002"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-SESSIONINVALID");
        verify(sessions).findActiveByToken(TenantId.of("2001"), "missing-token");
        verifyNoInteractions(users, audits);
    }

    @Test
    void logoutRevokesTokenAndPublishesAuditEvent() {
        LoginSessionRepository sessions = mock(LoginSessionRepository.class);
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        RbacAuthorizationQueryPort authorizations = mock(RbacAuthorizationQueryPort.class);
        ConsoleMenuQueryPort menuQuery = mock(ConsoleMenuQueryPort.class);
        when(sessions.findActiveByToken(TenantId.of("2001"), "token-1001")).thenReturn(Optional.of(activeSession()));
        when(users.findById(TenantId.of("2001"), "1001")).thenReturn(Optional.of(activeUser()));
        IdentitySessionApplicationService service = new IdentitySessionApplicationService(
                sessions,
                users,
                audits,
                authorizations,
                menuQuery,
                FIXED_CLOCK
        );

        service.logout(new LogoutCommand(
                TenantId.of("2001"),
                "token-1001",
                "TRACE-LOGOUT-001"
        ));

        verify(sessions).revokeByToken(TenantId.of("2001"), "token-1001");
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("LOGOUT_SUCCESS");
        assertThat(eventCaptor.getValue().traceId()).isEqualTo("TRACE-LOGOUT-001");
    }

    private static LoginSession activeSession() {
        return new LoginSession(
                TenantId.of("2001"),
                "token-1001",
                "1001",
                new AccessToken("token-1001", Instant.parse("2026-05-22T03:30:00Z")),
                Instant.parse("2026-05-22T02:00:00Z"),
                true
        );
    }

    private static EnterpriseUser activeUser() {
        return new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin", "auditor"),
                UserStatus.ACTIVE,
                Instant.parse("2026-05-21T00:00:00Z"),
                Instant.parse("2026-05-21T01:00:00Z")
        );
    }

    private static CurrentUserMenuResult validMenu() {
        return new CurrentUserMenuResult(
                "admin.model-providers",
                "模型",
                "ADMIN",
                "/admin/model-providers",
                "AdminModelProviders",
                "admin/model-providers/list",
                "model-hub:provider:read",
                20
        );
    }

}
