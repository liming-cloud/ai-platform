package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TenantMemberEstablishmentApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final SubjectId SUBJECT = new SubjectId(4921825928971026435L);

    @Test
    void 访问管理员建立已启用全局主体时必须创建无权限普通成员() {
        var committed = new AtomicReference<TenantMembership>();
        var service = new TenantMemberEstablishmentApplicationService(
                store(committed, Optional.of(SUBJECT), false), () -> 3382738893456539651L);

        var view = service.establish(manager(), SUBJECT.value());

        assertEquals("3382738893456539651", view.membershipId());
        assertEquals("ENABLED", view.status());
        assertEquals(0L, committed.get().permissionVersion());
    }

    @Test
    void 停用或不存在的全局主体不得建立成员资格() {
        var service = new TenantMemberEstablishmentApplicationService(
                store(new AtomicReference<>(), Optional.empty(), false), () -> 3382738893456539651L);

        assertThrows(IllegalArgumentException.class,
                () -> service.establish(manager(), SUBJECT.value()));
    }

    @Test
    void 同租户重复主体不得建立第二个成员资格() {
        var service = new TenantMemberEstablishmentApplicationService(
                store(new AtomicReference<>(), Optional.of(SUBJECT), true), () -> 3382738893456539651L);

        assertThrows(IllegalArgumentException.class,
                () -> service.establish(manager(), SUBJECT.value()));
    }

    private static TenantMemberEstablishmentStore store(
            AtomicReference<TenantMembership> committed,
            Optional<SubjectId> activeSubject,
            boolean exists) {
        return new TenantMemberEstablishmentStore() {
            @Override public Optional<SubjectId> lockActiveSubject(SubjectId subjectId) { return activeSubject; }
            @Override public boolean hasMembership(TenantId tenantId, SubjectId subjectId) { return exists; }
            @Override public void insert(TenantMembership membership) { committed.set(membership); }
        };
    }

    private static TrustedRequestContext manager() {
        return new TrustedRequestContext(TENANT,
                new com.aiplatform.foundation.security.identity.SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L), CredentialType.PERSON, AccessChannel.CONSOLE,
                2L, Set.of(new PermissionCode("access:manage")), "trc_member_establish");
    }
}
