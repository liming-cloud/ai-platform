package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
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
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantMemberStatusApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId TARGET = new MemberId(3382738893456539650L);

    @Test
    void 有权管理员停用非最后访问管理员时必须提交并撤销旧会话() {
        var committed = new AtomicReference<TenantMembership>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(committed, revoked, 1L);

        var view = service.disable(manager(), TARGET.value());

        assertEquals("DISABLED", view.status());
        assertEquals(4L, view.permissionVersion());
        assertEquals(TenantMembershipStatus.DISABLED, committed.get().status());
        assertEquals(TARGET, revoked.get());
    }

    @Test
    void 停用最后访问管理员时不得提交或撤销会话() {
        var committed = new AtomicReference<TenantMembership>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(committed, revoked, 0L);

        assertThrows(IllegalStateException.class, () -> service.disable(manager(), TARGET.value()));

        assertNull(committed.get());
        assertNull(revoked.get());
    }

    @Test
    void 有权管理员启用已停用成员时必须推进权限修订并撤销旧会话() {
        var committed = new AtomicReference<TenantMembership>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(committed, revoked, 1L, disabledMember());

        var view = service.enable(manager(), TARGET.value());

        assertEquals("ENABLED", view.status());
        assertEquals(5L, view.permissionVersion());
        assertEquals(TenantMembershipStatus.ENABLED, committed.get().status());
        assertEquals(TARGET, revoked.get());
    }

    private static TenantMemberStatusApplicationService service(
            AtomicReference<TenantMembership> committed,
            AtomicReference<MemberId> revoked,
            long otherAccessManagers) {
        TenantMemberStatusStore store = new TenantMemberStatusStore() {
            @Override
            public Optional<TenantMembership> lock(TenantId tenantId, MemberId membershipId) {
                return TENANT.equals(tenantId) && TARGET.equals(membershipId)
                        ? Optional.of(enabledMember()) : Optional.empty();
            }

            @Override
            public boolean hasAccessManage(TenantId tenantId, MemberId membershipId) {
                return TENANT.equals(tenantId) && TARGET.equals(membershipId);
            }

            @Override
            public long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId) {
                return otherAccessManagers;
            }

            @Override
            public void update(TenantMembership membership) {
                committed.set(membership);
            }
        };
        TenantMemberSessionRevoker sessions = revoked::set;
        return new TenantMemberStatusApplicationService(store, sessions);
    }

    private static TenantMemberStatusApplicationService service(
            AtomicReference<TenantMembership> committed,
            AtomicReference<MemberId> revoked,
            long otherAccessManagers,
            TenantMembership membership) {
        TenantMemberStatusStore store = new TenantMemberStatusStore() {
            @Override
            public Optional<TenantMembership> lock(TenantId tenantId, MemberId membershipId) {
                return TENANT.equals(tenantId) && TARGET.equals(membershipId)
                        ? Optional.of(membership) : Optional.empty();
            }

            @Override public boolean hasAccessManage(TenantId tenantId, MemberId membershipId) { return false; }
            @Override public long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId) { return otherAccessManagers; }
            @Override public void update(TenantMembership updated) { committed.set(updated); }
        };
        return new TenantMemberStatusApplicationService(store, revoked::set);
    }

    private static TenantMembership enabledMember() {
        return TenantMembership.reconstitute(
                TARGET,
                TENANT,
                new SubjectId(4921825928971026434L),
                TenantMembershipRole.MEMBER,
                TenantMembershipStatus.ENABLED,
                3L,
                7L,
                Instant.parse("2026-08-10T08:00:00Z"));
    }

    private static TenantMembership disabledMember() {
        return enabledMember().disable();
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(
                TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539649L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                Set.of(new PermissionCode("access:manage")),
                "trc_member_status");
    }
}
