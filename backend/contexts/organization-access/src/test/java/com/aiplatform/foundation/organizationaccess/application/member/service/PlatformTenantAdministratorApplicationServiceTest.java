package com.aiplatform.foundation.organizationaccess.application.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantAdministratorGovernance;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformTenantAdministratorApplicationServiceTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant NOW = Instant.parse("2026-08-31T03:00:00Z");
    private static final MemberId PREVIOUS = new MemberId(501L);
    private static final SubjectId PREVIOUS_SUBJECT = new SubjectId(51L);
    private static final SubjectId CANDIDATE_SUBJECT = new SubjectId(52L);

    @Test
    void 平台管理员可把既有普通成员分配为租户管理员并撤销其全部会话() {
        var store = new RecordingStore();
        store.candidateMembership = Optional.of(membership(
                502L, CANDIDATE_SUBJECT, TenantMembershipRole.TENANT_USER, TenantMembershipStatus.ENABLED, 2L));
        var revoked = new LinkedHashSet<SubjectId>();
        var service = service(store, revoked);

        var view = service.assign(platformAdministrator(), TENANT.toString(), CANDIDATE_SUBJECT.value());

        assertEquals("candidate-admin", view.loginName());
        assertEquals("TENANT_ADMINISTRATOR", view.role());
        assertEquals("ENABLED", view.status());
        assertEquals(CANDIDATE_SUBJECT, revoked.iterator().next());
        assertEquals(TenantMembershipRole.TENANT_ADMINISTRATOR,
                store.assignment.administrator().role());
    }

    @Test
    void 停用最后一个启用管理员必须失败且不撤销会话() {
        var store = new RecordingStore();
        store.otherEnabledAdministrators = 0L;
        var revoked = new LinkedHashSet<SubjectId>();
        var service = service(store, revoked);

        assertThrows(OrganizationAccessRuleViolation.class,
                () -> service.disable(platformAdministrator(), TENANT.toString(), PREVIOUS.value(), 5L));

        assertNull(store.updated);
        assertEquals(Set.of(), revoked);
    }

    @Test
    void 替换管理员必须在同一结果中保存新管理员和停用旧管理员并撤销双方会话() {
        var store = new RecordingStore();
        var revoked = new LinkedHashSet<SubjectId>();
        var service = service(store, revoked);

        var view = service.replace(
                platformAdministrator(),
                TENANT.toString(),
                PREVIOUS.value(),
                5L,
                CANDIDATE_SUBJECT.value());

        assertEquals("candidate-admin", view.loginName());
        assertEquals(TenantMembershipStatus.DISABLED, store.replacement.previousAdministrator().status());
        assertEquals(Set.of(CANDIDATE_SUBJECT, PREVIOUS_SUBJECT), revoked);
    }

    @Test
    void 恢复管理员时必须再次拒绝活动平台管理员() {
        var store = new RecordingStore();
        store.activePlatformAdministrator = true;
        store.previous = membership(
                PREVIOUS.value(), PREVIOUS_SUBJECT, TenantMembershipRole.TENANT_ADMINISTRATOR,
                TenantMembershipStatus.DISABLED, 5L);
        var revoked = new LinkedHashSet<SubjectId>();
        var service = service(store, revoked);

        assertThrows(OrganizationAccessRuleViolation.class,
                () -> service.restore(platformAdministrator(), TENANT.toString(), PREVIOUS.value(), 5L));

        assertNull(store.updated);
        assertEquals(Set.of(), revoked);
    }

    @Test
    void 已任其他租户管理员的主体不得在当前租户分配或替换() {
        var store = new RecordingStore();
        store.administratorInOtherTenant = true;
        var revoked = new LinkedHashSet<SubjectId>();
        var service = service(store, revoked);

        assertThrows(OrganizationAccessRuleViolation.class,
                () -> service.assign(platformAdministrator(), TENANT.toString(), CANDIDATE_SUBJECT.value()));
        assertThrows(OrganizationAccessRuleViolation.class,
                () -> service.replace(platformAdministrator(), TENANT.toString(), PREVIOUS.value(), 5L,
                        CANDIDATE_SUBJECT.value()));

        assertNull(store.assignment);
        assertNull(store.replacement);
        assertEquals(Set.of(), revoked);
    }

    @Test
    void 恢复时发现跨租户管理员事实必须拒绝且不撤销会话() {
        var store = new RecordingStore();
        store.administratorInOtherTenant = true;
        store.previous = membership(
                PREVIOUS.value(), PREVIOUS_SUBJECT, TenantMembershipRole.TENANT_ADMINISTRATOR,
                TenantMembershipStatus.DISABLED, 5L);
        var revoked = new LinkedHashSet<SubjectId>();

        assertThrows(OrganizationAccessRuleViolation.class,
                () -> service(store, revoked).restore(
                        platformAdministrator(), TENANT.toString(), PREVIOUS.value(), 5L));

        assertNull(store.updated);
        assertEquals(Set.of(), revoked);
    }

    private static PlatformTenantAdministratorApplicationService service(
            RecordingStore store,
            Set<SubjectId> revoked) {
        return new PlatformTenantAdministratorApplicationService(
                store,
                revoked::add,
                () -> 9001L,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PlatformRequestContext platformAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(1L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(
                        PlatformPermissions.SESSION_ACCESS,
                        PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_platform_tenant_administrator");
    }

    private static TenantMembership membership(
            long membershipId,
            SubjectId subjectId,
            TenantMembershipRole role,
            TenantMembershipStatus status,
            long version) {
        return TenantMembership.reconstitute(
                new MemberId(membershipId), TENANT, subjectId, role, status, version, version, NOW);
    }

    private static SubjectIdentity subject(SubjectId subjectId, String loginName) {
        return SubjectIdentity.register(
                subjectId, loginName, new EncodedPassword("encoded-password"), NOW);
    }

    private static final class RecordingStore implements PlatformTenantAdministratorStore {

        private TenantMembership previous = membership(
                PREVIOUS.value(), PREVIOUS_SUBJECT, TenantMembershipRole.TENANT_ADMINISTRATOR,
                TenantMembershipStatus.ENABLED, 5L);
        private Optional<TenantMembership> candidateMembership = Optional.empty();
        private long otherEnabledAdministrators = 1L;
        private boolean activePlatformAdministrator;
        private boolean administratorInOtherTenant;
        private TenantAdministratorGovernance.Assignment assignment;
        private TenantAdministratorGovernance.Replacement replacement;
        private TenantMembership updated;

        @Override
        public void lockTenantGovernanceScope(TenantId tenantId) {
            if (!tenantId.equals(TENANT)) {
                throw new IllegalArgumentException("租户治理范围不可用");
            }
        }

        @Override
        public Optional<SubjectIdentity> lockActiveSubject(SubjectId subjectId) {
            if (subjectId.equals(CANDIDATE_SUBJECT)) {
                return Optional.of(subject(subjectId, "candidate-admin"));
            }
            if (subjectId.equals(PREVIOUS_SUBJECT)) {
                return Optional.of(subject(subjectId, "previous-admin"));
            }
            return Optional.empty();
        }

        @Override
        public Optional<SubjectIdentity> lockSubjectForMembership(TenantId tenantId, MemberId membershipId) {
            return tenantId.equals(TENANT) && membershipId.equals(PREVIOUS)
                    ? Optional.of(subject(PREVIOUS_SUBJECT, "previous-admin")) : Optional.empty();
        }

        @Override
        public boolean hasActivePlatformAdministrator(SubjectId subjectId) {
            return activePlatformAdministrator;
        }

        @Override
        public boolean hasAdministratorInOtherTenant(SubjectId subjectId, TenantId tenantId) {
            return administratorInOtherTenant;
        }

        @Override
        public Optional<TenantMembership> lockMembershipBySubject(TenantId tenantId, SubjectId subjectId) {
            return tenantId.equals(TENANT) && subjectId.equals(CANDIDATE_SUBJECT)
                    ? candidateMembership : Optional.empty();
        }

        @Override
        public Optional<TenantMembership> lockMembership(TenantId tenantId, MemberId membershipId) {
            return tenantId.equals(TENANT) && membershipId.equals(PREVIOUS)
                    ? Optional.of(previous) : Optional.empty();
        }

        @Override
        public long countOtherEnabledAdministrators(TenantId tenantId, MemberId membershipId) {
            return otherEnabledAdministrators;
        }

        @Override
        public void saveAssignment(TenantAdministratorGovernance.Assignment result) {
            assignment = result;
        }

        @Override
        public void saveReplacement(TenantAdministratorGovernance.Replacement result) {
            replacement = result;
        }

        @Override
        public void update(TenantMembership membership) {
            updated = membership;
        }
    }
}
