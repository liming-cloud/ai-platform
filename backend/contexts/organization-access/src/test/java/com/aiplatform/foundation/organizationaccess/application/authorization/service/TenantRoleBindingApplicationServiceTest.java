package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantRoleBindingCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleBindingResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantRoleBindingApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId TARGET = new MemberId(3382738893456539650L);
    private static final Instant NOW = Instant.parse("2026-08-10T02:00:00Z");

    @Test
    void 受权绑定应原子提交权限修订并撤销目标成员租户会话() {
        var committed = new AtomicReference<TenantRoleBindingResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(Optional.of(member()), false, committed, revoked);

        var result = service.bind(manager(), new TenantRoleBindingCommand(
                TARGET.value(), TenantBuiltInRole.TENANT_ADMINISTRATOR.name()));

        assertEquals(1L, committed.get().revisedMembership().permissionVersion());
        assertEquals(TARGET, revoked.get());
        assertEquals(1L, result.permissionVersion());
    }

    @Test
    void 无访问管理权限时不得读取目标成员或撤销会话() {
        var committed = new AtomicReference<TenantRoleBindingResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(Optional.of(member()), false, committed, revoked);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.bind(reader(), new TenantRoleBindingCommand(
                        TARGET.value(), TenantBuiltInRole.TENANT_ADMINISTRATOR.name())));
        assertNull(committed.get());
        assertNull(revoked.get());
    }

    @Test
    void 已有角色绑定时不得推进权限版本或撤销会话() {
        var committed = new AtomicReference<TenantRoleBindingResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(Optional.of(member()), true, committed, revoked);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.bind(manager(), new TenantRoleBindingCommand(
                        TARGET.value(), TenantBuiltInRole.TENANT_ADMINISTRATOR.name())));

        assertNull(committed.get());
        assertNull(revoked.get());
    }

    private static TenantRoleBindingApplicationService service(
            Optional<TenantMembership> membership,
            boolean alreadyBound,
            AtomicReference<TenantRoleBindingResult> committed,
            AtomicReference<MemberId> revoked) {
        TenantRoleBindingStore store = new TenantRoleBindingStore() {
            @Override
            public Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId) {
                return TENANT.equals(tenantId) && TARGET.equals(membershipId) ? membership : Optional.empty();
            }

            @Override
            public boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantBuiltInRole role) {
                return alreadyBound;
            }

            @Override
            public void commit(TenantRoleBindingResult result) {
                committed.set(result);
            }
        };
        TenantMemberSessionRevoker sessions = revoked::set;
        return new TenantRoleBindingApplicationService(store, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TrustedRequestContext manager() {
        return context(Set.of(new PermissionCode("access:manage")));
    }

    private static TrustedRequestContext reader() {
        return context(Set.of(new PermissionCode("knowledge:read")));
    }

    private static TrustedRequestContext context(Set<PermissionCode> permissions) {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539649L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                permissions,
                "trc_role_binding");
    }

    private static TenantMembership member() {
        return TenantMembership.reconstitute(
                TARGET,
                TENANT,
                new SubjectId(4921825928971026434L),
                TenantMembershipRole.TENANT_USER,
                TenantMembershipStatus.ENABLED,
                0L,
                1L,
                NOW.minusSeconds(60));
    }
}
