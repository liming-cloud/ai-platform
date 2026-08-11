package com.aiplatform.foundation.security.satoken.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SaTokenPlatformSessionGatewayTest {

    private static final AtomicLong LOGIN_TYPES = new AtomicLong(550000000000000000L);

    @Test
    void 应以独立登录类型签发并恢复平台上下文() {
        var gateway = gateway(new SaTokenDaoDefaultImpl(), "platform-test-");
        var expected = context();

        var issued = gateway.issue(expected);

        assertEquals(expected, gateway.resolve(issued.tokenValue()));
    }

    @Test
    void 不得将其他登录类型的令牌解释为平台会话() {
        var dao = new SaTokenDaoDefaultImpl();
        var tenantGateway = new SaTokenSessionGateway(
                new ContextBoundStpLogic(
                        "tenant-test-" + LOGIN_TYPES.incrementAndGet(),
                        dao,
                        config()),
                Duration.ofMinutes(30));
        var platformGateway = gateway(dao, "platform-test-");
        var tenantToken = tenantGateway.issue(new com.aiplatform.foundation.security.identity.TrustedRequestContext(
                new com.aiplatform.foundation.shared.kernel.tenant.TenantId(100000000000000001L),
                new SubjectId(4921825928971026434L),
                new com.aiplatform.foundation.security.identity.MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("knowledge:read")),
                "trc_tenant"));

        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> platformGateway.resolve(tenantToken.tokenValue()));
    }

    @Test
    void 按主体撤销应使该主体全部平台会话立即失效() {
        var gateway = gateway(new SaTokenDaoDefaultImpl(), "platform-test-");
        var first = gateway.issue(context());
        var second = gateway.issue(context());

        gateway.revokeSubjectSessions(context().subjectId());

        assertThrows(InvalidAuthenticatedSessionException.class, () -> gateway.resolve(first.tokenValue()));
        assertThrows(InvalidAuthenticatedSessionException.class, () -> gateway.resolve(second.tokenValue()));
    }

    private static SaTokenPlatformSessionGateway gateway(SaTokenDaoDefaultImpl dao, String prefix) {
        return new SaTokenPlatformSessionGateway(
                new ContextBoundStpLogic(
                        prefix + LOGIN_TYPES.incrementAndGet(),
                        dao,
                        config()),
                Duration.ofMinutes(30));
    }

    private static SaTokenConfig config() {
        return new SaTokenConfig()
                .setTokenName("foundation-token")
                .setTimeout(1800)
                .setIsConcurrent(false)
                .setIsShare(false);
    }

    private static PlatformRequestContext context() {
        return new PlatformRequestContext(
                new SubjectId(4921825928971026434L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                3L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_platform");
    }
}
