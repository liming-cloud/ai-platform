package com.aiplatform.identity.application;

import com.aiplatform.identity.application.command.CurrentUserCommand;
import com.aiplatform.identity.application.command.LogoutCommand;
import com.aiplatform.identity.application.error.IdentityErrorCodes;
import com.aiplatform.identity.application.port.ConsoleMenuQueryPort;
import com.aiplatform.identity.application.result.AuthorizedConsoleMenuSnapshot;
import com.aiplatform.identity.application.result.CurrentUserResult;
import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.identity.domain.port.EnterpriseUserRepository;
import com.aiplatform.identity.domain.port.LoginSessionRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 身份会话应用服务，负责从登录态恢复当前用户以及租户隔离的会话失效。
 */
@Service
public class IdentitySessionApplicationService {
    private static final String RESOURCE_TYPE_USER = "IAM_USER";
    private static final String ACTION_LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
    private static final String RISK_LEVEL_LOW = "LOW";

    private final LoginSessionRepository sessions;
    private final EnterpriseUserRepository users;
    private final AuditEventPublisher auditPublisher;
    private final RbacAuthorizationQueryPort authorizationQueryPort;
    private final ConsoleMenuQueryPort consoleMenuQueryPort;
    private final Clock clock;

    public IdentitySessionApplicationService(
            LoginSessionRepository sessions,
            EnterpriseUserRepository users,
            AuditEventPublisher auditPublisher,
            RbacAuthorizationQueryPort authorizationQueryPort,
            ConsoleMenuQueryPort consoleMenuQueryPort,
            Clock clock
    ) {
        this.sessions = Objects.requireNonNull(sessions, "登录态仓储不能为空");
        this.users = Objects.requireNonNull(users, "用户仓储不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计事件发布端口不能为空");
        this.authorizationQueryPort = Objects.requireNonNull(authorizationQueryPort, "RBAC 授权查询端口不能为空");
        this.consoleMenuQueryPort = Objects.requireNonNull(consoleMenuQueryPort, "控制台菜单查询端口不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public CurrentUserResult currentUser(CurrentUserCommand command) {
        Objects.requireNonNull(command, "当前用户查询命令不能为空");
        LoginSession session = requireActiveSession(command.tenantId(), command.tokenValue());
        EnterpriseUser user = requireActiveUser(session);
        Set<PermissionCode> permissionCodes = authorizationQueryPort.findPermissionCodes(
                user.tenantId(),
                "user",
                user.userId()
        );
        AuthorizedConsoleMenuSnapshot consoleMenus = consoleMenuQueryPort.findAuthorizedMenus(
                user.tenantId(),
                "user",
                user.userId(),
                user.roleCodes(),
                permissionCodes
        );
        return new CurrentUserResult(
                user.tenantId(),
                user.userId(),
                user.loginName(),
                user.displayName(),
                user.roleCodes(),
                permissionCodes,
                consoleMenus.menus(),
                consoleMenus.defaultRoute(),
                session.accessToken().expiresAt()
        );
    }

    public void logout(LogoutCommand command) {
        Objects.requireNonNull(command, "登出命令不能为空");
        LoginSession session = requireActiveSession(command.tenantId(), command.tokenValue());
        EnterpriseUser user = requireActiveUser(session);
        sessions.revokeByToken(command.tenantId(), command.tokenValue());
        auditPublisher.publish(logoutSuccessEvent(command, user));
    }

    private LoginSession requireActiveSession(com.aiplatform.shared.kernel.TenantId tenantId, String tokenValue) {
        LoginSession session = sessions.findActiveByToken(tenantId, tokenValue)
                .orElseThrow(() -> new ApplicationException(
                        IdentityErrorCodes.SESSION_INVALID,
                        "租户ID=" + tenantId.value()
                ));
        if (!session.canBeUsedAt(Instant.now(clock))) {
            throw new ApplicationException(IdentityErrorCodes.SESSION_INVALID, "会话ID=" + session.sessionId());
        }
        return session;
    }

    private EnterpriseUser requireActiveUser(LoginSession session) {
        EnterpriseUser user = users.findById(session.tenantId(), session.userId())
                .orElseThrow(() -> new ApplicationException(
                        IdentityErrorCodes.SESSION_INVALID,
                        "用户ID=" + session.userId()
                ));
        if (!user.canLogin()) {
            throw new ApplicationException(IdentityErrorCodes.USER_DISABLED, "用户ID=" + user.userId());
        }
        return user;
    }

    private AuditEvent logoutSuccessEvent(LogoutCommand command, EnterpriseUser user) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                command.tenantId().value(),
                user.userId(),
                RESOURCE_TYPE_USER,
                user.userId(),
                ACTION_LOGOUT_SUCCESS,
                RISK_LEVEL_LOW,
                null,
                command.traceId(),
                Instant.now(clock)
        );
    }
}
