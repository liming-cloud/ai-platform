package com.aiplatform.foundation.security.satoken.session;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.session.IssuedSession;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * 使用 Sa-Token 保存服务端会话状态的认证会话适配器。
 */
public final class SaTokenSessionGateway implements AuthenticatedSessionGateway {

    private static final Object DAO_MONITOR = new Object();
    private static final String TENANT_ID = "tenantId";
    private static final String SUBJECT_ID = "subjectId";
    private static final String MEMBERSHIP_ID = "membershipId";
    private static final String CREDENTIAL_TYPE = "credentialType";
    private static final String CHANNEL = "channel";
    private static final String PERMISSION_VERSION = "permissionVersion";
    private static final String PERMISSIONS = "permissions";
    private static final String TRACE_ID = "traceId";

    private final StpLogic stpLogic;
    private final SaTokenDao saTokenDao;
    private final Duration timeout;

    public SaTokenSessionGateway(
            StpLogic stpLogic,
            SaTokenDao saTokenDao,
            Duration timeout) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "Sa-Token 逻辑对象不能为空");
        this.saTokenDao = Objects.requireNonNull(saTokenDao, "Sa-Token 数据访问器不能为空");
        this.timeout = Objects.requireNonNull(timeout, "会话有效期不能为空");
        if (timeout.isZero() || timeout.isNegative() || timeout.getNano() != 0) {
            throw new IllegalArgumentException("会话有效期必须是正整数秒");
        }
    }

    @Override
    public IssuedSession issue(TrustedRequestContext context) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        return withBoundDao(() -> issueBound(context));
    }

    private IssuedSession issueBound(TrustedRequestContext context) {
        String tokenValue = null;
        try {
            var parameter = SaLoginParameter.create()
                    .setTimeout(timeout.getSeconds())
                    .setDeviceType(context.channel().name().toLowerCase(java.util.Locale.ROOT))
                    .setIsConcurrent(false)
                    .setIsShare(false)
                    .setRightNowCreateTokenSession(true);
            tokenValue = stpLogic.createLoginSession(
                    Long.toString(context.membershipId().value()),
                    parameter);
            writeContext(stpLogic.getTokenSessionByToken(tokenValue, true), context);
            return new IssuedSession(stpLogic.getTokenName(), tokenValue, timeout);
        } catch (RuntimeException exception) {
            if (tokenValue != null) {
                stpLogic.logoutByTokenValue(tokenValue);
            }
            throw exception;
        }
    }

    @Override
    public TrustedRequestContext resolve(String tokenValue) {
        validateToken(tokenValue);
        return withBoundDao(() -> resolveBound(tokenValue));
    }

    private TrustedRequestContext resolveBound(String tokenValue) {
        try {
            var loginId = stpLogic.getLoginIdByToken(tokenValue);
            var session = stpLogic.getTokenSessionByToken(tokenValue, false);
            if (loginId == null || session == null) {
                throw new InvalidAuthenticatedSessionException();
            }

            var membershipId = MembershipId.parse(requiredString(session, MEMBERSHIP_ID));
            if (!membershipId.toString().equals(loginId.toString())) {
                throw new InvalidAuthenticatedSessionException();
            }
            return new TrustedRequestContext(
                    TenantId.parse(requiredString(session, TENANT_ID)),
                    SubjectId.parse(requiredString(session, SUBJECT_ID)),
                    membershipId,
                    CredentialType.valueOf(requiredString(session, CREDENTIAL_TYPE)),
                    AccessChannel.valueOf(requiredString(session, CHANNEL)),
                    requiredLong(session, PERMISSION_VERSION),
                    requiredPermissions(session),
                    requiredString(session, TRACE_ID));
        } catch (InvalidAuthenticatedSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAuthenticatedSessionException();
        }
    }

    @Override
    public void revoke(String tokenValue) {
        validateToken(tokenValue);
        withBoundDao(() -> {
            stpLogic.logoutByTokenValue(tokenValue);
            return null;
        });
    }

    private <T> T withBoundDao(Supplier<T> operation) {
        synchronized (DAO_MONITOR) {
            SaManager.setSaTokenDao(saTokenDao);
            return operation.get();
        }
    }

    private static void writeContext(SaSession session, TrustedRequestContext context) {
        var permissions = new TreeSet<String>();
        for (var permission : context.permissions()) {
            permissions.add(permission.value());
        }
        session.set(TENANT_ID, context.tenantId().toString())
                .set(SUBJECT_ID, context.subjectId().toString())
                .set(MEMBERSHIP_ID, context.membershipId().toString())
                .set(CREDENTIAL_TYPE, context.credentialType().name())
                .set(CHANNEL, context.channel().name())
                .set(PERMISSION_VERSION, context.permissionVersion())
                .set(PERMISSIONS, permissions)
                .set(TRACE_ID, context.traceId());
    }

    private static String requiredString(SaSession session, String key) {
        var value = session.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidAuthenticatedSessionException();
        }
        return text;
    }

    private static long requiredLong(SaSession session, String key) {
        var value = session.get(key);
        if (!(value instanceof Number number)) {
            throw new InvalidAuthenticatedSessionException();
        }
        return number.longValue();
    }

    private static Set<PermissionCode> requiredPermissions(SaSession session) {
        var value = session.get(PERMISSIONS);
        if (!(value instanceof Collection<?> values)) {
            throw new InvalidAuthenticatedSessionException();
        }
        var permissions = new java.util.HashSet<PermissionCode>();
        for (var item : values) {
            if (!(item instanceof String text)) {
                throw new InvalidAuthenticatedSessionException();
            }
            permissions.add(new PermissionCode(text));
        }
        return Set.copyOf(permissions);
    }

    private static void validateToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank() || tokenValue.length() > 512) {
            throw new InvalidAuthenticatedSessionException();
        }
    }
}
