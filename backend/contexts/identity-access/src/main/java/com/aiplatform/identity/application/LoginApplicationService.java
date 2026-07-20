package com.aiplatform.identity.application;

import com.aiplatform.identity.application.command.LoginCommand;
import com.aiplatform.identity.application.error.IdentityErrorCodes;
import com.aiplatform.identity.application.result.LoginResult;
import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.identity.domain.port.AccessTokenIssuer;
import com.aiplatform.identity.domain.port.EnterpriseUserRepository;
import com.aiplatform.identity.domain.port.LoginAttemptDecision;
import com.aiplatform.identity.domain.port.LoginAttemptProtection;
import com.aiplatform.identity.domain.port.PasswordVerifier;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 登录应用服务，只编排身份上下文端口和共享审计能力，不直接依赖 Web、Sa-Token 或其他上下文应用服务。
 */
@Service
public class LoginApplicationService {
    private static final String RESOURCE_TYPE_USER = "IAM_USER";
    private static final String ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    private static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    private static final String RISK_LEVEL_LOW = "LOW";
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";
    private static final String AUDIT_RESULT = "result";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String RESULT_RATE_LIMITED = "RATE_LIMITED";
    private static final String RESULT_PROTECTION_UNAVAILABLE = "PROTECTION_UNAVAILABLE";

    private final EnterpriseUserRepository users;
    private final PasswordVerifier passwordVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final LoginAttemptProtection loginAttemptProtection;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    public LoginApplicationService(
            EnterpriseUserRepository users,
            PasswordVerifier passwordVerifier,
            AccessTokenIssuer accessTokenIssuer,
            LoginAttemptProtection loginAttemptProtection,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.users = Objects.requireNonNull(users, "用户仓储不能为空");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "密码校验端口不能为空");
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer, "访问令牌签发端口不能为空");
        this.loginAttemptProtection = Objects.requireNonNull(loginAttemptProtection, "登录尝试防护端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计事件发布端口不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "登录命令不能为空");
        Instant now = Instant.now(clock);
        LoginAttemptDecision inspection = loginAttemptProtection.inspect(
                command.tenantId(),
                command.loginName()
        );
        if (inspection != LoginAttemptDecision.ALLOWED) {
            throw rejectProtection(command, null, null, now, inspection);
        }

        Optional<EnterpriseUser> existingUser = users.findByLoginName(command.tenantId(), command.loginName());
        EnterpriseUser user = existingUser.orElse(null);
        boolean passwordMatches = verifyPassword(command, user);
        if (user == null || !user.canLogin() || !passwordMatches) {
            throw rejectAuthentication(
                    command,
                    user == null ? null : user.userId(),
                    user == null ? null : user.userId(),
                    now
            );
        }

        LoginAttemptDecision reset = loginAttemptProtection.reset(command.tenantId(), command.loginName());
        if (reset != LoginAttemptDecision.ALLOWED) {
            throw rejectProtection(command, user.userId(), user.userId(), now, reset);
        }

        LoginSession session = accessTokenIssuer.issue(user, now);
        auditPublisher.publish(loginSuccessEvent(command, user, now));
        return new LoginResult(
                user.tenantId(),
                user.userId(),
                user.displayName(),
                user.roleCodes(),
                session.accessToken().tokenValue(),
                session.accessToken().expiresAt()
        );
    }

    private ApplicationException rejectAuthentication(
            LoginCommand command,
            String actorId,
            String resourceId,
            Instant now
    ) {
        LoginAttemptDecision decision = loginAttemptProtection.recordFailure(
                command.tenantId(),
                command.loginName()
        );
        if (decision != LoginAttemptDecision.ALLOWED) {
            return rejectProtection(command, actorId, resourceId, now, decision);
        }
        auditPublisher.publish(loginFailedEvent(
                command,
                actorId,
                resourceId,
                now,
                RESULT_INVALID_CREDENTIALS
        ));
        return new ApplicationException(IdentityErrorCodes.AUTH_FAILED, "登录凭据无效");
    }

    private ApplicationException rejectProtection(
            LoginCommand command,
            String actorId,
            String resourceId,
            Instant now,
            LoginAttemptDecision decision
    ) {
        boolean rateLimited = decision == LoginAttemptDecision.RATE_LIMITED;
        String result = rateLimited ? RESULT_RATE_LIMITED : RESULT_PROTECTION_UNAVAILABLE;
        auditPublisher.publish(loginFailedEvent(command, actorId, resourceId, now, result));
        return rateLimited
                ? new ApplicationException(IdentityErrorCodes.LOGIN_RATE_LIMITED, "登录失败已达到安全限制")
                : new ApplicationException(
                        IdentityErrorCodes.AUTH_PROTECTION_UNAVAILABLE,
                        "登录防护服务不可用"
                );
    }

    private boolean verifyPassword(LoginCommand command, EnterpriseUser user) {
        if (user == null) {
            passwordVerifier.performDummyVerification(command.rawPassword());
            return false;
        }
        return passwordVerifier.matches(user, command.rawPassword());
    }

    private AuditEvent loginSuccessEvent(LoginCommand command, EnterpriseUser user, Instant now) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                command.tenantId().value(),
                user.userId(),
                RESOURCE_TYPE_USER,
                user.userId(),
                ACTION_LOGIN_SUCCESS,
                RISK_LEVEL_LOW,
                null,
                command.traceId(),
                now,
                Map.of(AUDIT_RESULT, RESULT_SUCCESS)
        );
    }

    private AuditEvent loginFailedEvent(
            LoginCommand command,
            String actorId,
            String resourceId,
            Instant now,
            String result
    ) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                command.tenantId().value(),
                actorId,
                RESOURCE_TYPE_USER,
                resourceId,
                ACTION_LOGIN_FAILED,
                RISK_LEVEL_MEDIUM,
                null,
                command.traceId(),
                now,
                Map.of(AUDIT_RESULT, result)
        );
    }
}
