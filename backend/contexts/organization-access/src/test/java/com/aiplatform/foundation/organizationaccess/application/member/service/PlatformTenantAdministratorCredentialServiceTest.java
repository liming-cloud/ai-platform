package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformTenantAdministratorCredentialServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final MemberId MEMBER = new MemberId(501L);
    private static final SubjectId SUBJECT = new SubjectId(51L);
    private final PlatformTenantAdministratorStore administrators = mock(PlatformTenantAdministratorStore.class);
    private final PlatformAccountStore accounts = mock(PlatformAccountStore.class);

    @Test
    void 当前租户管理员重置全局密码后撤销全部旧会话且清除口令副本() {
        when(administrators.lockMembership(TENANT, MEMBER)).thenReturn(Optional.of(membership(TENANT)));
        when(accounts.lock(SUBJECT)).thenReturn(Optional.of(subject()));
        var revoked = new java.util.ArrayList<SubjectId>();
        var service = new PlatformTenantAdministratorCredentialService(
                administrators, accounts, revoked::add, password -> "replacement-digest");
        var password = new char[]{'a', 'b', 'c'};

        service.resetPassword(operator(), TENANT.toString(), MEMBER.value(), 5L, password);

        var updated = ArgumentCaptor.forClass(SubjectIdentity.class);
        verify(accounts).update(updated.capture());
        assertEquals(SUBJECT, updated.getValue().id());
        assertEquals("replacement-digest", updated.getValue().encodedPassword().verificationValue());
        assertEquals(1L, updated.getValue().version());
        assertEquals(java.util.List.of(SUBJECT), revoked);
        assertArrayEquals(new char[3], password);
    }

    @Test
    void 错租户或非管理员成员不得重置全局身份() {
        when(administrators.lockMembership(TENANT, MEMBER)).thenReturn(Optional.of(membership(new TenantId(2L))));
        var service = new PlatformTenantAdministratorCredentialService(
                administrators, accounts, subjectId -> { throw new AssertionError("不得撤销会话"); },
                password -> "unused");

        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(operator(), TENANT.toString(), MEMBER.value(), 5L, new char[]{'a'}));
        verify(accounts, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 旧成员版本不得重置全局身份() {
        when(administrators.lockMembership(TENANT, MEMBER)).thenReturn(Optional.of(membership(TENANT)));
        var service = new PlatformTenantAdministratorCredentialService(
                administrators, accounts, subjectId -> { throw new AssertionError("不得撤销会话"); },
                password -> "unused");

        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(operator(), TENANT.toString(), MEMBER.value(), 4L, new char[]{'a'}));
        verify(accounts, never()).update(org.mockito.ArgumentMatchers.any());
    }

    private static PlatformRequestContext operator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(1L),
                CredentialType.PERSON, AccessChannel.CONSOLE, 1L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_tenant_admin_reset");
    }

    private static TenantMembership membership(TenantId tenantId) {
        return TenantMembership.reconstitute(MEMBER, tenantId, SUBJECT,
                TenantMembershipRole.TENANT_ADMINISTRATOR, TenantMembershipStatus.ENABLED,
                5L, 5L, Instant.parse("2026-09-01T00:00:00Z"));
    }

    private static SubjectIdentity subject() {
        return SubjectIdentity.reconstitute(SUBJECT, "synthetic-admin",
                new EncodedPassword("previous-digest"),
                com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus.ACTIVE,
                0L, Instant.parse("2026-09-01T00:00:00Z"));
    }
}
