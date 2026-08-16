package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantRoleRevocationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantRoleRevocationResult;
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

/** 验证内置角色撤销必须与权限修订和旧租户会话撤销作为同一用例提交。 */
class TenantRoleRevocationApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId TARGET = new MemberId(3382738893456539650L);
    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");

    @Test
    void 受权撤销应原子提交权限修订并撤销目标成员租户会话() {
        var committed = new AtomicReference<TenantRoleRevocationResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(true, false, 1L, committed, revoked);

        var result = service.revoke(manager(), new TenantRoleRevocationCommand(
                TARGET.value(), TenantBuiltInRole.TENANT_ADMINISTRATOR.name()));

        assertEquals(1L, result.permissionVersion());
        assertEquals(TARGET, revoked.get());
        assertEquals(TenantBuiltInRole.TENANT_ADMINISTRATOR, committed.get().role());
    }

    @Test
    void 撤销最后一个访问管理员时不得写入或撤销会话() {
        var committed = new AtomicReference<TenantRoleRevocationResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(true, false, 0L, committed, revoked);

        assertThrows(IllegalStateException.class, () -> service.revoke(
                manager(), new TenantRoleRevocationCommand(
                        TARGET.value(), TenantBuiltInRole.TENANT_ADMINISTRATOR.name())));

        assertNull(committed.get());
        assertNull(revoked.get());
    }

    private static TenantRoleRevocationApplicationService service(
            boolean bound,
            boolean retainsAccessManage,
            long others,
            AtomicReference<TenantRoleRevocationResult> committed,
            AtomicReference<MemberId> revoked) {
        TenantRoleRevocationStore store = new TenantRoleRevocationStore() {
            @Override
            public Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId membershipId) {
                return TENANT.equals(tenantId) && TARGET.equals(membershipId)
                        ? Optional.of(member()) : Optional.empty();
            }

            @Override
            public boolean hasBinding(TenantId tenantId, MemberId membershipId, TenantBuiltInRole role) {
                return bound;
            }

            @Override
            public boolean retainsAccessManageAfterRevocation(
                    TenantId tenantId, MemberId membershipId, TenantBuiltInRole role) {
                return retainsAccessManage;
            }

            @Override
            public long countOtherEnabledAccessManagers(TenantId tenantId, MemberId membershipId) {
                return others;
            }

            @Override
            public void commit(TenantRoleRevocationResult result) {
                committed.set(result);
            }
        };
        TenantMemberSessionRevoker sessions = revoked::set;
        return new TenantRoleRevocationApplicationService(store, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
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
                "trc_role_revocation");
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
