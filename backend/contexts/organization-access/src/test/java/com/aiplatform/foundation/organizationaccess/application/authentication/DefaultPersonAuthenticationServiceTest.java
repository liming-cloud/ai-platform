package com.aiplatform.foundation.organizationaccess.application.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.PersonAuthenticationUseCase;
import com.aiplatform.foundation.organizationaccess.api.model.AccessChannel;
import com.aiplatform.foundation.organizationaccess.api.model.IssuedSession;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.organizationaccess.api.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.api.model.PersonLoginCommand;
import com.aiplatform.foundation.organizationaccess.api.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.port.out.TenantMemberCredentialRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultPersonAuthenticationServiceTest {

    private static final TenantId TENANT_ID =
            new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final SubjectId SUBJECT_ID =
            new SubjectId(4921825928971026434L);
    private static final MembershipId MEMBERSHIP_ID =
            new MembershipId(3382738893456539650L);
    private static final PermissionCode KNOWLEDGE_WRITE =
            new PermissionCode("knowledge:write");

    @Test
    void 应使用仓储事实签发可信租户会话() {
        var captured = new AtomicReference<TrustedRequestContext>();
        PersonAuthenticationUseCase service = service(
                Optional.of(member()),
                (password, encoded) -> encoded.equals("valid") && new String(password).equals("secret"),
                context -> {
                    captured.set(context);
                    return new IssuedSession("satoken", "token", Duration.ofHours(1));
                });

        IssuedSession result;
        try (var command = command("secret")) {
            result = service.authenticate(command);
        }

        assertEquals("token", result.tokenValue());
        assertEquals(TENANT_ID, captured.get().tenantId());
        assertEquals(MEMBERSHIP_ID, captured.get().membershipId());
        assertTrue(captured.get().hasPermission(KNOWLEDGE_WRITE));
    }

    @Test
    void 未知账号也应执行等价摘要验证并返回统一失败() {
        var verifiedHash = new AtomicReference<String>();
        PersonAuthenticationUseCase service = service(
                Optional.empty(),
                (password, encoded) -> {
                    verifiedHash.set(encoded);
                    return false;
                },
                context -> {
                    throw new AssertionError("认证失败时不得签发会话");
                });

        try (var command = command("wrong")) {
            var error = assertThrows(InvalidCredentialsException.class, () -> service.authenticate(command));
            assertEquals("租户或登录凭据无效", error.getMessage());
        }
        assertEquals("dummy", verifiedHash.get());
    }

    @Test
    void 错误密码不得泄漏账号是否存在() {
        PersonAuthenticationUseCase service = service(
                Optional.of(member()),
                (password, encoded) -> false,
                context -> {
                    throw new AssertionError("认证失败时不得签发会话");
                });

        try (var command = command("wrong")) {
            var error = assertThrows(InvalidCredentialsException.class, () -> service.authenticate(command));
            assertEquals("租户或登录凭据无效", error.getMessage());
        }
    }

    private static DefaultPersonAuthenticationService service(
            Optional<AuthenticatedMember> member,
            PasswordVerifier passwordVerifier,
            SessionIssuer sessionIssuer) {
        TenantMemberCredentialRepository repository =
                (tenantCode, loginName) -> member;
        AuthenticatedSessionGateway sessions = new AuthenticatedSessionGateway() {
            @Override
            public IssuedSession issue(TrustedRequestContext context) {
                return sessionIssuer.issue(context);
            }

            @Override
            public TrustedRequestContext resolve(String tokenValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void revoke(String tokenValue) {
                throw new UnsupportedOperationException();
            }
        };
        return new DefaultPersonAuthenticationService(repository, passwordVerifier, sessions, "dummy");
    }

    private static AuthenticatedMember member() {
        return new AuthenticatedMember(
                TENANT_ID,
                SUBJECT_ID,
                MEMBERSHIP_ID,
                "valid",
                2,
                Set.of(KNOWLEDGE_WRITE));
    }

    private static PersonLoginCommand command(String password) {
        return new PersonLoginCommand(
                "demo-campus",
                "Admin",
                password.toCharArray(),
                AccessChannel.CONSOLE,
                "trc_login");
    }

    @FunctionalInterface
    private interface SessionIssuer {
        IssuedSession issue(TrustedRequestContext context);
    }
}
