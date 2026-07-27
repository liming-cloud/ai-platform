package com.aiplatform.foundation.evaluationoperations.domain.authorization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationsAccessPolicyTest {

    private final OperationsAccessPolicy policy = new OperationsAccessPolicy();

    @Test
    void 用量和审计权限应独立判定() {
        var usageReader = context("usage:read");

        assertDoesNotThrow(() -> policy.requireUsageRead(usageReader));
        assertThrows(
                OperationsPermissionDeniedException.class,
                () -> policy.requireAuditRead(usageReader));
    }

    private static TrustedRequestContext context(String permission) {
        return new TrustedRequestContext(
                new TenantId(1L),
                new SubjectId(2L),
                new MembershipId(3L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                Set.of(new PermissionCode(permission)),
                "trc_operations");
    }
}
