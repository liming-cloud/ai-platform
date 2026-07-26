package com.aiplatform.foundation.organizationaccess.api.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrustedRequestContextTest {

    private static final PermissionCode KNOWLEDGE_READ =
            new PermissionCode("knowledge:read");

    @Test
    void 应保存不可变权限快照并提供精确权限判断() {
        var permissions = new HashSet<>(Set.of(KNOWLEDGE_READ));
        var context = context(permissions);
        permissions.clear();

        assertTrue(context.hasPermission(KNOWLEDGE_READ));
        assertFalse(context.hasPermission(new PermissionCode("knowledge:write")));
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.permissions().clear());
    }

    @Test
    void 应拒绝无效权限版本和空白追踪标识() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrustedRequestContext(
                        tenantId(),
                        subjectId(),
                        membershipId(),
                        CredentialType.PERSON,
                        AccessChannel.CONSOLE,
                        -1,
                        Set.of(KNOWLEDGE_READ),
                        "trc_demo"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrustedRequestContext(
                        tenantId(),
                        subjectId(),
                        membershipId(),
                        CredentialType.PERSON,
                        AccessChannel.CONSOLE,
                        1,
                        Set.of(KNOWLEDGE_READ),
                        " "));
    }

    @Test
    void 应拒绝格式不明确的权限编码() {
        assertThrows(IllegalArgumentException.class, () -> new PermissionCode("knowledge"));
        assertThrows(IllegalArgumentException.class, () -> new PermissionCode("Knowledge:Read"));
        assertThrows(IllegalArgumentException.class, () -> new PermissionCode("knowledge:*"));
    }

    private static TrustedRequestContext context(Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                tenantId(),
                subjectId(),
                membershipId(),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1,
                permissions,
                "trc_demo");
    }

    private static TenantId tenantId() {
        return new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    }

    private static SubjectId subjectId() {
        return new SubjectId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    }

    private static MembershipId membershipId() {
        return new MembershipId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
    }
}
