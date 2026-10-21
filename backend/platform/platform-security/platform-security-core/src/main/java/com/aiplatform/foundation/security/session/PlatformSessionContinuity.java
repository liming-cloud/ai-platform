package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.web.BearerTokens;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** 仅当前服务端请求持有的会话连续性检查；不序列化 token，不持久化或输出其内容。 */
public final class PlatformSessionContinuity implements BooleanSupplier {
    public static final String REQUEST_ATTRIBUTE = PlatformSessionContinuity.class.getName();
    private final PlatformSessionGateway sessions;
    private final String token;
    private final PlatformRequestContext admitted;

    public PlatformSessionContinuity(PlatformSessionGateway sessions, String token, PlatformRequestContext admitted) {
        this.sessions = Objects.requireNonNull(sessions);
        this.token = BearerTokens.requireTokenValue(token);
        this.admitted = Objects.requireNonNull(admitted);
    }

    /** 只比较同一已准入身份和权限修订；追踪标识不是身份。 */
    public boolean matches(PlatformRequestContext context) {
        return context != null && admitted.subjectId().equals(context.subjectId())
                && admitted.permissionVersion() == context.permissionVersion()
                && admitted.credentialType() == context.credentialType() && admitted.channel() == context.channel()
                && admitted.permissions().equals(context.permissions());
    }

    @Override
    public boolean getAsBoolean() {
        try { return matches(sessions.resolve(token)); }
        catch (RuntimeException exception) { return false; }
    }

    @Override
    public String toString() { return "PlatformSessionContinuity[已脱敏]"; }
}
