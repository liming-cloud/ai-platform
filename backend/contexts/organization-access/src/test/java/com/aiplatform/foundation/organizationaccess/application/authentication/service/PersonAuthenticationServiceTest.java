package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.organizationaccess.application.authentication.command.PersonLoginCommand;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import java.util.Optional;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PersonAuthenticationServiceTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final SubjectId SUBJECT_ID =
            new SubjectId(4921825928971026434L);
    private static final MemberId MEMBERSHIP_ID =
            new MemberId(3382738893456539650L);
    private static final PermissionCode KNOWLEDGE_WRITE =
            new PermissionCode("knowledge:write");

    @Test
    void 应使用仓储事实签发可信租户会话() {
        var captured = new AtomicReference<TrustedRequestContext>();
        PersonAuthenticationService service = service(
                Optional.of(subject()),
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
        assertEquals(MEMBERSHIP_ID.value(), captured.get().membershipId().value());
        assertTrue(captured.get().hasPermission(
                new com.aiplatform.foundation.security.identity.PermissionCode(
                        KNOWLEDGE_WRITE.value())));
    }

    @Test
    void 未知账号也应执行等价摘要验证并返回统一失败() {
        var verifiedHash = new AtomicReference<String>();
        PersonAuthenticationService service = service(
                Optional.empty(),
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
        PersonAuthenticationService service = service(
                Optional.of(subject()),
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

    private static PersonAuthenticationService service(
            Optional<SubjectIdentity> subject,
            Optional<MemberAuthenticationProfile> member,
            PasswordVerifier passwordVerifier,
            SessionIssuer sessionIssuer) {
        SubjectIdentityRepository identities = loginName -> subject;
        TenantMembershipRepository memberships =
                (tenantCode, subjectId) -> member;
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
        return new PersonAuthenticationService(
                identities,
                memberships,
                passwordVerifier,
                sessions,
                "dummy");
    }

    private static MemberAuthenticationProfile member() {
        return new MemberAuthenticationProfile(
                TENANT_ID,
                SUBJECT_ID,
                MEMBERSHIP_ID,
                new PermissionGrantSet(2, Set.of(KNOWLEDGE_WRITE)));
    }

    private static SubjectIdentity subject() {
        return SubjectIdentity.register(
                SUBJECT_ID,
                "admin",
                new EncodedPassword("valid"),
                Instant.parse("2026-08-03T00:00:00Z"));
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
