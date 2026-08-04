package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.PlatformLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PlatformAccessGrant;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PlatformAuthenticationServiceTest {

    private static final SubjectId SUBJECT_ID = new SubjectId(4921825928971026436L);

    @Test
    void 应在主体凭据和平台授权均有效时签发平台会话() {
        var captured = new AtomicReference<PlatformRequestContext>();
        var service = service(
                Optional.of(subject()),
                Optional.of(grant()),
                (password, encoded) -> encoded.equals("valid") && new String(password).equals("secret"),
                context -> {
                    captured.set(context);
                    return new IssuedSession("platform-token", "token", Duration.ofHours(1));
                });

        try (var command = command("secret")) {
            assertEquals("token", service.authenticate(command).tokenValue());
        }

        assertEquals(SUBJECT_ID.value(), captured.get().subjectId().value());
        assertEquals(true, captured.get().hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE));
    }

    @Test
    void 缺少平台授权仍应完成等价密码验证并统一失败() {
        var checked = new AtomicReference<String>();
        var service = service(
                Optional.of(subject()),
                Optional.empty(),
                (password, encoded) -> {
                    checked.set(encoded);
                    return true;
                },
                context -> {
                    throw new AssertionError("缺少授权不得签发平台会话");
                });

        try (var command = command("secret")) {
            assertThrows(InvalidCredentialsException.class, () -> service.authenticate(command));
        }
        assertEquals("valid", checked.get());
    }

    private static PlatformAuthenticationService service(
            Optional<SubjectIdentity> subject,
            Optional<PlatformAccessGrant> grant,
            PasswordVerifier passwords,
            SessionIssuer issuer) {
        SubjectIdentityRepository identities = loginName -> subject;
        PlatformAccessGrantRepository grants = subjectId -> grant;
        PlatformSessionGateway sessions = new PlatformSessionGateway() {
            @Override
            public IssuedSession issue(PlatformRequestContext context) {
                return issuer.issue(context);
            }

            @Override
            public PlatformRequestContext resolve(String tokenValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void revoke(String tokenValue) {
                throw new UnsupportedOperationException();
            }
        };
        return new PlatformAuthenticationService(identities, grants, passwords, sessions, "dummy");
    }

    private static SubjectIdentity subject() {
        return SubjectIdentity.register(
                SUBJECT_ID,
                "platform-admin",
                new EncodedPassword("valid"),
                Instant.parse("2026-08-03T00:00:00Z"));
    }

    private static PlatformAccessGrant grant() {
        return new PlatformAccessGrant(SUBJECT_ID, 2L, Set.of(
                new PermissionCode("platform-session:access"),
                new PermissionCode("tenant-governance:write")));
    }

    private static PlatformLoginCommand command(String password) {
        return new PlatformLoginCommand(
                "platform-admin",
                password.toCharArray(),
                AccessChannel.CONSOLE,
                "trc_platform_login");
    }

    @FunctionalInterface
    private interface SessionIssuer {
        IssuedSession issue(PlatformRequestContext context);
    }
}
