package com.aiplatform.foundation.security.satoken.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
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
    private SaTokenDao dao;
    private SaTokenSessionGateway gateway;

    @BeforeEach
    void setUp() {
        var loginType = "member-test-" + LOGIN_TYPES.incrementAndGet();
        var config = new SaTokenConfig()
                .setTokenName("foundation-token")
                .setTimeout(1800)
                .setIsConcurrent(false)
                .setIsShare(false);
        dao = new SaTokenDaoDefaultImpl();
        logic = new ContextBoundStpLogic(loginType, dao, config);
        gateway = new SaTokenSessionGateway(
                logic,
                Duration.ofMinutes(30));
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
        logic.getTokenSessionByToken(issued.tokenValue()).set("tenantId", "非法标识");

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
                .set("membershipId", "非法成员标识");

        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> gateway.resolve(issued.tokenValue()));
    }

    @Test
    void 多应用上下文交替访问时应恢复各自的数据访问器() {
        var first = gateway.issue(context());
        var secondLogic = new ContextBoundStpLogic(
                "member-test-" + LOGIN_TYPES.incrementAndGet(),
                new SaTokenDaoDefaultImpl(),
                new SaTokenConfig()
                        .setTokenName("foundation-token")
                        .setTimeout(1800)
                        .setIsConcurrent(false)
                        .setIsShare(false));
        var second = new SaTokenSessionGateway(
                secondLogic,
                Duration.ofMinutes(30));

        second.issue(context());

        assertEquals(context(), gateway.resolve(first.tokenValue()));
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
