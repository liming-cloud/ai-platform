package com.aiplatform.shared.security.context;

import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.util.Optional;

public final class PrincipalContext {
    private static final ThreadLocal<AuthenticatedPrincipal> CURRENT = new ThreadLocal<>();

    private PrincipalContext() {
    }

    public static void set(AuthenticatedPrincipal principal) {
        CURRENT.set(principal);
    }

    public static Optional<AuthenticatedPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static AuthenticatedPrincipal requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("认证主体上下文未初始化"));
    }

    public static String currentActorId() {
        return requireCurrent().actorId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
