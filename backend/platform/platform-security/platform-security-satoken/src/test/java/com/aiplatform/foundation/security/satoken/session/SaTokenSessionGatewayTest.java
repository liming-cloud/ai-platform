package com.aiplatform.foundation.security.satoken.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaTokenSessionGatewayTest {

    private static final AtomicLong LOGIN_TYPES =
            new AtomicLong(540000000000000000L);
    private static final MembershipId MEMBERSHIP_ID =
            new MembershipId(3382738893456539650L);
    private StpLogic logic;
    private SaTokenSessionGateway gateway;

    @BeforeEach
    void setUp() {
        var loginType = "member-test-" + LOGIN_TYPES.incrementAndGet();
        logic = new StpLogic(loginType).setConfig(new SaTokenConfig()
                .setTokenName("foundation-token")
                .setTimeout(1800)
                .setIsConcurrent(false)
                .setIsShare(false));
        gateway = new SaTokenSessionGateway(logic, Duration.ofMinutes(30));
    }

    @Test
    void 应使用真实SaToken会话签发并恢复可信上下文() {
        var expected = context();

        var issued = gateway.issue(expected);
        var actual = gateway.resolve(issued.tokenValue());

        assertEquals("foundation-token", issued.tokenName());
        assertEquals(Duration.ofMinutes(30), issued.expiresIn());
        assertEquals(expected, actual);
    }

    @Test
    void 应在令牌会话字段损坏时失败关闭() {
        var issued = gateway.issue(context());
        logic.getTokenSessionByToken(issued.tokenValue()).set("tenantId", "not-a-uuid");

        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> gateway.resolve(issued.tokenValue()));
    }

    @Test
    void 应在显式注销后拒绝旧令牌() {
        var issued = gateway.issue(context());

        gateway.revoke(issued.tokenValue());

        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> gateway.resolve(issued.tokenValue()));
    }

    @Test
    void 应拒绝登录标识与会话成员不一致的令牌() {
        var issued = gateway.issue(context());
        logic.getTokenSessionByToken(issued.tokenValue())
                .set("membershipId", "30000000-0000-0000-0000-000000000099");

        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> gateway.resolve(issued.tokenValue()));
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(4921825928971026434L),
                MEMBERSHIP_ID,
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                3,
                Set.of(
                        new PermissionCode("knowledge:read"),
                        new PermissionCode("knowledge:write")),
                "trc_session_test");
    }
}
