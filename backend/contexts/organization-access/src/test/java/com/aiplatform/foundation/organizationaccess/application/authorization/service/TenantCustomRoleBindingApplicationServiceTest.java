package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleBindingCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleBindingResult;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantCustomRoleBindingApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId MEMBER = new MemberId(3382738893456539650L);
    private static final TenantCustomRoleId ROLE = new TenantCustomRoleId(9001L);
    private static final Instant NOW = Instant.parse("2026-08-10T06:00:00Z");

    @Test
    void 受权管理员绑定活动自定义角色时必须推进成员权限并撤销旧会话() {
        var committed = new AtomicReference<TenantCustomRoleBindingResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(committed, revoked);

        var view = service.bind(manager(), new TenantCustomRoleBindingCommand(MEMBER.value(), ROLE.value()));

        assertEquals(1L, view.permissionVersion());
        assertEquals(MEMBER, committed.get().revisedMembership().id());
        assertEquals(MEMBER, revoked.get());
    }

    @Test
    void 无访问管理权限不得读取角色或撤销会话() {
        var committed = new AtomicReference<TenantCustomRoleBindingResult>();
        var revoked = new AtomicReference<MemberId>();
        var service = service(committed, revoked);

        assertThrows(IllegalArgumentException.class,
                () -> service.bind(reader(), new TenantCustomRoleBindingCommand(MEMBER.value(), ROLE.value())));
        assertNull(committed.get());
        assertNull(revoked.get());
    }

    private static TenantCustomRoleBindingApplicationService service(
            AtomicReference<TenantCustomRoleBindingResult> committed,
            AtomicReference<MemberId> revoked) {
        TenantCustomRoleBindingStore store = new TenantCustomRoleBindingStore() {
            @Override public Optional<TenantMembership> lockEnabled(TenantId tenantId, MemberId memberId) {
                return TENANT.equals(tenantId) && MEMBER.equals(memberId) ? Optional.of(member()) : Optional.empty();
            }
            @Override public Optional<TenantCustomRole> lockActive(TenantId tenantId, TenantCustomRoleId roleId) {
                return TENANT.equals(tenantId) && ROLE.equals(roleId) ? Optional.of(role()) : Optional.empty();
            }
            @Override public boolean hasBinding(TenantId tenantId, MemberId memberId, TenantCustomRoleId roleId) { return false; }
            @Override public void commit(TenantCustomRoleBindingResult result) { committed.set(result); }
        };
        TenantMemberSessionRevoker sessions = revoked::set;
        return new TenantCustomRoleBindingApplicationService(store, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TenantMembership member() { return TenantMembership.reconstitute(MEMBER, TENANT, new SubjectId(4921825928971026434L), TenantMembershipRole.TENANT_USER, TenantMembershipStatus.ENABLED, 0L, 1L, NOW); }
    private static TenantCustomRole role() { return TenantCustomRole.create(ROLE, TENANT, "模型观察员", Set.of(new PermissionCode("model:read")), NOW); }
    private static TrustedRequestContext manager() { return context(Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("access:manage"))); }
    private static TrustedRequestContext reader() { return context(Set.of(new com.aiplatform.foundation.security.identity.PermissionCode("model:read"))); }
    private static TrustedRequestContext context(Set<com.aiplatform.foundation.security.identity.PermissionCode> permissions) { return new TrustedRequestContext(TENANT, new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L), new MembershipId(3382738893456539649L), CredentialType.PERSON, AccessChannel.CONSOLE, 2L, permissions, "trc_custom_role_binding"); }
}
