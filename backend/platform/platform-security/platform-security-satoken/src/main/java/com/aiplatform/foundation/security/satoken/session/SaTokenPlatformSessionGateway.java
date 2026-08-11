package com.aiplatform.foundation.security.satoken.session;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.session.AuthenticatedSessionUnavailableException;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 使用独立 Sa-Token login type 保存平台治理会话的适配器。
 *
 * <p>该类型与租户会话使用不同的会话命名空间；平台解析失败时不尝试回退到租户会话。</p>
 */
public final class SaTokenPlatformSessionGateway implements PlatformSessionGateway {

    private static final String SUBJECT_ID = "subjectId";
    private static final String CREDENTIAL_TYPE = "credentialType";
    private static final String CHANNEL = "channel";
    private static final String PERMISSION_VERSION = "permissionVersion";
    private static final String PERMISSIONS = "permissions";
    private static final String TRACE_ID = "traceId";

    private final StpLogic stpLogic;
    private final Duration timeout;

    public SaTokenPlatformSessionGateway(StpLogic stpLogic, Duration timeout) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "Sa-Token 逻辑对象不能为空");
        this.timeout = Objects.requireNonNull(timeout, "会话有效期不能为空");
        if (timeout.isZero() || timeout.isNegative() || timeout.getNano() != 0) {
            throw new IllegalArgumentException("会话有效期必须是正整数秒");
        }
    }

    @Override
    public IssuedSession issue(PlatformRequestContext context) {
        Objects.requireNonNull(context, "平台请求上下文不能为空");
        String tokenValue = null;
        try {
            var parameter = SaLoginParameter.create()
                    .setTimeout(timeout.getSeconds())
                    .setDeviceType(context.channel().name().toLowerCase(java.util.Locale.ROOT))
                    .setIsConcurrent(false)
                    .setIsShare(false)
                    .setRightNowCreateTokenSession(true);
            tokenValue = stpLogic.createLoginSession(
                    Long.toString(context.subjectId().value()), parameter);
            writeContext(stpLogic.getTokenSessionByToken(tokenValue, true), context);
            return new IssuedSession(stpLogic.getTokenName(), tokenValue, timeout);
        } catch (RuntimeException exception) {
            if (tokenValue != null) {
                revokeAfterFailedIssue(tokenValue, exception);
            }
            throw new AuthenticatedSessionUnavailableException(exception);
        }
    }

    @Override
    public PlatformRequestContext resolve(String tokenValue) {
        validateToken(tokenValue);
        final Object loginId;
        final SaSession session;
        try {
            loginId = stpLogic.getLoginIdByToken(tokenValue);
            session = stpLogic.getTokenSessionByToken(tokenValue, false);
            if (loginId == null || session == null) {
                throw new InvalidAuthenticatedSessionException();
            }
        } catch (InvalidAuthenticatedSessionException | NotLoginException exception) {
            throw new InvalidAuthenticatedSessionException();
        } catch (RuntimeException exception) {
            throw new AuthenticatedSessionUnavailableException(exception);
        }
        try {
            var subjectId = SubjectId.parse(requiredString(session, SUBJECT_ID));
            if (!subjectId.toString().equals(loginId.toString())) {
                throw new InvalidAuthenticatedSessionException();
            }
            return new PlatformRequestContext(
                    subjectId,
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
        try {
            stpLogic.logoutByTokenValue(tokenValue);
        } catch (NotLoginException exception) {
            throw new InvalidAuthenticatedSessionException();
        } catch (RuntimeException exception) {
            throw new AuthenticatedSessionUnavailableException(exception);
        }
    }

    @Override
    public void revokeSubjectSessions(
            com.aiplatform.foundation.security.identity.SubjectId subjectId) {
        subjectId = Objects.requireNonNull(subjectId, "主体标识不能为空");
        try {
            stpLogic.logout(Long.toString(subjectId.value()));
        } catch (RuntimeException exception) {
            throw new AuthenticatedSessionUnavailableException(exception);
        }
    }

    private void revokeAfterFailedIssue(String tokenValue, RuntimeException original) {
        try {
            stpLogic.logoutByTokenValue(tokenValue);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static void writeContext(SaSession session, PlatformRequestContext context) {
        var permissions = new TreeSet<String>();
        for (var permission : context.permissions()) {
            permissions.add(permission.value());
        }
        session.set(SUBJECT_ID, context.subjectId().toString())
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
