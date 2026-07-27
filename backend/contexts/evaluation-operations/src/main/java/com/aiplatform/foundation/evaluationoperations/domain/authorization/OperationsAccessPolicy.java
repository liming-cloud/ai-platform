package com.aiplatform.foundation.evaluationoperations.domain.authorization;

import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 评测运营上下文的领域授权策略。
 */
public final class OperationsAccessPolicy {

    private static final PermissionCode USAGE_READ =
            new PermissionCode("usage:read");
    private static final PermissionCode AUDIT_READ =
            new PermissionCode("audit:read");

    public void requireUsageRead(TrustedRequestContext context) {
        require(context, USAGE_READ);
    }

    public void requireAuditRead(TrustedRequestContext context) {
        require(context, AUDIT_READ);
    }

    private static void require(
            TrustedRequestContext context,
            PermissionCode permission) {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(permission)) {
            throw new OperationsPermissionDeniedException();
        }
    }
}
