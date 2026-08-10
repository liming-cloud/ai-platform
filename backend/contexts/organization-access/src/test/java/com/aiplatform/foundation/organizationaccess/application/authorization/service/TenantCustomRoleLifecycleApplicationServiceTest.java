package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleDisableCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRolePermissionChangeCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleLifecycleResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantCustomRoleLifecycleApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final TenantCustomRoleId ROLE = new TenantCustomRoleId(9001L);
    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Test
    void 受权管理员修改角色时提交角色与所有成员修订并撤销旧会话() {
        var committed = new AtomicReference<TenantCustomRoleLifecycleResult>();
        var revoked = new ArrayList<MemberId>();
        var service = service(committed, revoked);

        var view = service.changePermissions(manager(), new TenantCustomRolePermissionChangeCommand(
                ROLE.value(), Set.of("model:write")));

        assertEquals(Set.of("model:write"), view.permissionCodes());
        assertEquals(1L, committed.get().role().version());
        assertEquals(List.of(new MemberId(9001L), new MemberId(9002L)), revoked);
    }

    @Test
    void 受权管理员停用角色时提交停用结果并撤销所有受影响成员会话() {
        var committed = new AtomicReference<TenantCustomRoleLifecycleResult>();
        var revoked = new ArrayList<MemberId>();
        var service = service(committed, revoked);

        var view = service.disable(manager(), new TenantCustomRoleDisableCommand(ROLE.value()));

        assertEquals("DISABLED", view.status());
        assertEquals(List.of(new MemberId(9001L), new MemberId(9002L)), revoked);
    }

    @Test
    void 无访问管理权限时不得锁定角色或撤销会话() {
        var committed = new AtomicReference<TenantCustomRoleLifecycleResult>();
        var revoked = new ArrayList<MemberId>();
        var service = service(committed, revoked);

        assertThrows(IllegalArgumentException.class, () -> service.disable(
                reader(), new TenantCustomRoleDisableCommand(ROLE.value())));

        assertNull(committed.get());
        assertEquals(List.of(), revoked);
    }

    private static TenantCustomRoleLifecycleApplicationService service(
            AtomicReference<TenantCustomRoleLifecycleResult> committed,
            List<MemberId> revoked) {
        TenantCustomRoleLifecycleStore store = new TenantCustomRoleLifecycleStore() {
            @Override
            public Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId) {
                return TENANT.equals(tenantId) && ROLE.equals(roleId) ? Optional.of(role()) : Optional.empty();
            }

            @Override
            public List<TenantMembership> lockEnabledBoundMembers(TenantId tenantId, TenantCustomRoleId roleId) {
                return TENANT.equals(tenantId) && ROLE.equals(roleId)
                        ? List.of(member(9001L), member(9002L)) : List.of();
            }

            @Override
            public long countEnabledAccessManagersAfterChange(
                    TenantId tenantId, TenantCustomRoleId roleId, boolean retainsAccessManage) {
                return 1L;
            }

            @Override
            public void commit(TenantCustomRoleLifecycleResult result) {
                committed.set(result);
            }
        };
        TenantMemberSessionRevoker sessions = revoked::add;
        return new TenantCustomRoleLifecycleApplicationService(
                store, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TenantCustomRole role() {
        return TenantCustomRole.create(ROLE, TENANT, "模型观察员",
                Set.of(new PermissionCode("model:read")), NOW);
    }

    private static TenantMembership member(long memberId) {
        return TenantMembership.reconstitute(new MemberId(memberId), TENANT, new SubjectId(memberId + 10000L),
                TenantMembershipRole.MEMBER, TenantMembershipStatus.ENABLED, 0L, 0L, NOW);
    }

    private static TrustedRequestContext manager() {
        return context(Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("access:manage")));
    }

    private static TrustedRequestContext reader() {
        return context(Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("model:read")));
    }

    private static TrustedRequestContext context(
            Set<com.aiplatform.foundation.security.identity.PermissionCode> permissions) {
        return new TrustedRequestContext(TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539649L), CredentialType.PERSON, AccessChannel.CONSOLE,
                2L, permissions, "trc_custom_role_lifecycle");
    }
}
