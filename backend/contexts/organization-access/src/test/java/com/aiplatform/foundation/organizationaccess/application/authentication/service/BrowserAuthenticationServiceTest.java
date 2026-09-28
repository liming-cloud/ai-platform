package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.BrowserLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleAuthorization;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BrowserAuthenticationServiceTest {

    private static final SubjectId SUBJECT_ID = new SubjectId(4921825928971026434L);
    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final MemberId MEMBERSHIP_ID = new MemberId(3382738893456539650L);

    @Test
    void 应只校验一次主体口令并按已验证角色分别签发两类浏览器会话() {
        var passwordChecks = new AtomicInteger();
        var service = new BrowserAuthenticationService(
                loginName -> Optional.of(subject()),
                (tenantCode, subjectId) -> Optional.of(member()),
                subjectId -> Optional.of(platformGrant()),
                (password, encoded) -> {
                    passwordChecks.incrementAndGet();
                    return encoded.equals("valid") && new String(password).equals("secret");
                },
                tenantSessions(),
                platformSessions(),
                policy(),
                "dummy");

        try (var command = new BrowserLoginCommand(
                "demo-store",
                "platform-admin",
                "secret".toCharArray(),
                AccessChannel.CONSOLE,
                "trc_browser_login")) {
            var actual = service.authenticate(command);

            assertEquals("tenant-token", actual.tenantSession().orElseThrow().tokenValue());
            assertEquals("platform-token", actual.platformSession().orElseThrow().tokenValue());
        }

        assertEquals(1, passwordChecks.get());
    }

    private static AuthenticatedSessionGateway tenantSessions() {
        return new AuthenticatedSessionGateway() {
            @Override
            public IssuedSession issue(TrustedRequestContext context) {
                throw new AssertionError("租户登录必须提交当前策略时长");
            }

            @Override
            public IssuedSession issue(TrustedRequestContext context, Duration duration) {
                assertEquals(Duration.ofHours(8), duration);
                return new IssuedSession("satoken", "tenant-token", duration);
            }

            @Override
            public TrustedRequestContext resolve(String tokenValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void revoke(String tokenValue) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void revokeMembershipSessions(
                    com.aiplatform.foundation.security.identity.MembershipId membershipId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TenantLoginPolicyEnforcer policy() {
        TenantLoginSecurityPolicyStore policies = new TenantLoginSecurityPolicyStore() {
            @Override public Optional<TenantLoginSecurityPolicy> find(long tenantId) {
                return Optional.of(TenantLoginSecurityPolicy.defaults(TENANT_ID));
            }
            @Override public boolean replace(TenantLoginSecurityPolicy next, long expectedVersion) {
                throw new AssertionError("认证不写登录策略");
            }
        };
        TenantLoginAttemptStore attempts = new TenantLoginAttemptStore() {
            @Override public boolean isLocked(TenantId tenantId,
                    com.aiplatform.foundation.security.identity.SubjectId subjectId, Instant now) { return false; }
            @Override public void recordFailure(TenantId tenantId,
                    com.aiplatform.foundation.security.identity.SubjectId subjectId,
                    TenantLoginSecurityPolicy policy, Instant now) { }
            @Override public boolean clearIfUnlocked(TenantId tenantId,
                    com.aiplatform.foundation.security.identity.SubjectId subjectId, Instant now) { return true; }
        };
        return new TenantLoginPolicyEnforcer(policies, attempts,
                Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"), ZoneOffset.UTC));
    }

    private static PlatformSessionGateway platformSessions() {
        return new PlatformSessionGateway() {
            @Override
            public IssuedSession issue(PlatformRequestContext context) {
                assertTrue(context.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE));
                return new IssuedSession("satoken-platform", "platform-token", Duration.ofHours(1));
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
    }

    private static SubjectIdentity subject() {
        return SubjectIdentity.register(
                SUBJECT_ID,
                "platform-admin",
                new EncodedPassword("valid"),
                Instant.parse("2026-08-03T00:00:00Z"));
    }

    private static MemberAuthenticationProfile member() {
        return new MemberAuthenticationProfile(
                TENANT_ID,
                SUBJECT_ID,
                MEMBERSHIP_ID,
                new PermissionGrantSet(2, Set.of(new PermissionCode("knowledge:write"))));
    }

    private static PlatformRoleAuthorization platformGrant() {
        return new PlatformRoleAuthorization(
                SUBJECT_ID, 2L, Set.of(PlatformRole.PLATFORM_ADMINISTRATOR));
    }
}
