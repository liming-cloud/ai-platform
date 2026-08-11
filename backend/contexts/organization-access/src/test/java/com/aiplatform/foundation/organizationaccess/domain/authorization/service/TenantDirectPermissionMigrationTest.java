package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantDirectPermissionMigrationTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final MemberId MEMBERSHIP_ID = new MemberId(3382738893456539650L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T00:00:00Z");

    @Test
    void 只在明确批准撤销后以角色权限完整替换历史直接权限() {
        var result = TenantDirectPermissionMigration.migrate(
                enabledMembership(),
                Set.of(
                        permission("model:read"),
                        permission("model:write"),
                        permission("assistant:query"),
                        permission("model-tenant:write")),
                Set.of(permission("knowledge:read")),
                Set.of(
                        permission("knowledge:read"),
                        permission("model:read"),
                        permission("model:write"),
                        permission("assistant:query")),
                Set.of(permission("model-tenant:write")));

        assertEquals(4, result.removedDirectPermissionCount());
        assertEquals(4L, result.revisedMembership().permissionVersion());
        assertEquals(8L, result.revisedMembership().version());
        assertEquals(Set.of(permission("model-tenant:write")), result.approvedRevocations());
    }

    @Test
    void 缺失角色映射不得删除历史直接权限() {
        assertThrows(IllegalArgumentException.class, () -> TenantDirectPermissionMigration.migrate(
                enabledMembership(),
                Set.of(permission("model:read"), permission("assistant:query")),
                Set.of(),
                Set.of(permission("model:read")),
                Set.of()));
    }

    @Test
    void 未批准撤销和额外角色权限均失败关闭() {
        assertThrows(IllegalArgumentException.class, () -> TenantDirectPermissionMigration.migrate(
                enabledMembership(),
                Set.of(permission("model-tenant:write")),
                Set.of(),
                Set.of(),
                Set.of()));
        assertThrows(IllegalArgumentException.class, () -> TenantDirectPermissionMigration.migrate(
                enabledMembership(),
                Set.of(permission("model:read")),
                Set.of(),
                Set.of(permission("model:read"), permission("access:manage")),
                Set.of()));
    }

    @Test
    void 停用成员不得迁移权限() {
        assertThrows(IllegalStateException.class, () -> TenantDirectPermissionMigration.migrate(
                TenantMembership.reconstitute(
                        MEMBERSHIP_ID,
                        TENANT_ID,
                        new SubjectId(4921825928971026434L),
                        TenantMembershipRole.MEMBER,
                        TenantMembershipStatus.DISABLED,
                        3L,
                        7L,
                        CREATED_AT),
                Set.of(permission("model:read")),
                Set.of(),
                Set.of(permission("model:read")),
                Set.of()));
    }

    private static TenantMembership enabledMembership() {
        return TenantMembership.reconstitute(
                MEMBERSHIP_ID,
                TENANT_ID,
                new SubjectId(4921825928971026434L),
                TenantMembershipRole.MEMBER,
                TenantMembershipStatus.ENABLED,
                3L,
                7L,
                CREATED_AT);
    }

    private static PermissionCode permission(String value) {
        return new PermissionCode(value);
    }
}
