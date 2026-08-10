package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleCreationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantCustomRoleApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-10T04:00:00Z");

    @Test
    void 租户访问管理员可创建只包含已发布权限的自定义角色() {
        var saved = new AtomicReference<TenantCustomRole>();
        var service = service(saved);

        var result = service.create(manager(), new TenantCustomRoleCreationCommand(
                "模型观察员", Set.of("model:read")));

        assertEquals(9001L, result.id());
        assertEquals("模型观察员", result.name());
        assertEquals(Set.of("model:read"), result.permissionCodes());
        assertEquals(new TenantCustomRoleId(9001L), saved.get().id());
        assertEquals(NOW, saved.get().createdAt());
    }

    @Test
    void 无访问管理权限时不得创建自定义角色() {
        var saved = new AtomicReference<TenantCustomRole>();
        var service = service(saved);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(reader(), new TenantCustomRoleCreationCommand(
                        "模型观察员", Set.of("model:read"))));

        assertNull(saved.get());
    }

    private static TenantCustomRoleApplicationService service(
            AtomicReference<TenantCustomRole> saved) {
        TenantCustomRoleStore store = new TenantCustomRoleStore() {
            @Override
            public boolean hasActiveName(TenantId tenantId, String name) {
                return false;
            }

            @Override
            public void insert(TenantCustomRole role) {
                saved.set(role);
            }
        };
        EntityIdGenerator identifiers = () -> 9001L;
        return new TenantCustomRoleApplicationService(
                store, identifiers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TrustedRequestContext manager() {
        return context(Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("access:manage")));
    }

    private static TrustedRequestContext reader() {
        return context(Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("model:read")));
    }

    private static TrustedRequestContext context(
            Set<com.aiplatform.foundation.security.identity.PermissionCode> permissions) {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                permissions,
                "trc_custom_role");
    }
}
