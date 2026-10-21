package com.aiplatform.foundation.security.session;

import static org.junit.jupiter.api.Assertions.*;
import com.aiplatform.foundation.security.identity.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformSessionContinuityTest {
    @Test void 只改变追踪标识不改变当前会话身份() {
        var gateway = new Sessions();
        gateway.current = context(101, 1, "new-trace");
        var continuity = new PlatformSessionContinuity(gateway, "synthetic-session", context(101, 1, "old-trace"));
        assertTrue(continuity.getAsBoolean());
        assertFalse(continuity.toString().contains("synthetic-session"));
    }

    @Test void 退出换人或权限修订变化均不能延续原验证() {
        var gateway = new Sessions();
        var continuity = new PlatformSessionContinuity(gateway, "synthetic-session", context(101, 1, "old-trace"));
        gateway.current = context(102, 1, "changed-subject");
        assertFalse(continuity.getAsBoolean());
        gateway.current = context(101, 2, "changed-permission");
        assertFalse(continuity.getAsBoolean());
        gateway.revoke("synthetic-session");
        assertFalse(continuity.getAsBoolean());
    }

    private static final class Sessions implements PlatformSessionGateway {
        PlatformRequestContext current;
        public IssuedSession issue(PlatformRequestContext context) { throw new UnsupportedOperationException(); }
        public PlatformRequestContext resolve(String token) {
            assertEquals("synthetic-session", token);
            if (current == null) throw new InvalidAuthenticatedSessionException();
            return current;
        }
        public void revoke(String token) { current = null; }
    }

    private static PlatformRequestContext context(long id, long version, String trace) {
        return new PlatformRequestContext(new SubjectId(id), CredentialType.PERSON, AccessChannel.CONSOLE, version,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.MODEL_PLATFORM_WRITE), trace);
    }
}
