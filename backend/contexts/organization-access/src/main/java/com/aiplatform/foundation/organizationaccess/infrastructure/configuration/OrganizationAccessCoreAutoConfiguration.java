package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionCapabilityService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionTerminationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleRevocationApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingRevocationApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleLifecycleApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberStatusApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleTransitionService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformRoleBindingRepository;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.password.Pbkdf2PasswordAdapter;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

/**
 * 注册认证用例及其密码验证实现。
 */
@AutoConfiguration(after = OrganizationAccessPersistenceAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.organization-access.enabled",
        havingValue = "true")
public class OrganizationAccessCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Pbkdf2PasswordAdapter pbkdf2PasswordAdapter() {
        return new Pbkdf2PasswordAdapter(new SecureRandom());
    }

    @Bean
    @ConditionalOnMissingBean
    PersonAuthenticationService personAuthenticationService(
            SubjectIdentityRepository identities,
            TenantMembershipRepository memberships,
            AuthenticatedSessionGateway sessions,
            Pbkdf2PasswordAdapter passwords) {
        var dummyPassword = "不存在成员的等价验证口令".toCharArray();
        try {
            return new PersonAuthenticationService(
                    identities,
                    memberships,
                    passwords,
                    sessions,
                    passwords.encode(dummyPassword));
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAuthenticationService platformAuthenticationService(
            SubjectIdentityRepository identities,
            PlatformRoleBindingRepository bindings,
            PlatformSessionGateway sessions,
            Pbkdf2PasswordAdapter passwords) {
        var dummyPassword = "不存在主体的等价验证口令".toCharArray();
        try {
            return new PlatformAuthenticationService(
                    identities,
                    bindings,
                    passwords,
                    sessions,
                    passwords.encode(dummyPassword));
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Bean
    @ConditionalOnMissingBean
    BrowserAuthenticationService browserAuthenticationService(
            SubjectIdentityRepository identities,
            TenantMembershipRepository memberships,
            PlatformRoleBindingRepository bindings,
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions,
            Pbkdf2PasswordAdapter passwords) {
        var dummyPassword = "不存在主体的等价验证口令".toCharArray();
        try {
            return new BrowserAuthenticationService(
                    identities,
                    memberships,
                    bindings,
                    passwords,
                    tenantSessions,
                    platformSessions,
                    passwords.encode(dummyPassword));
        } finally {
            Arrays.fill(dummyPassword, '\0');
        }
    }

    @Bean
    @ConditionalOnMissingBean
    BrowserSessionCapabilityService browserSessionCapabilityService(
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions,
            @Value("${foundation.security.session-timeout:PT1H}") String sessionTimeout) {
        return new BrowserSessionCapabilityService(
                tenantSessions, platformSessions, Duration.parse(sessionTimeout));
    }

    @Bean
    @ConditionalOnMissingBean
    BrowserSessionTerminationService browserSessionTerminationService(
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions) {
        return new BrowserSessionTerminationService(tenantSessions, platformSessions);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberSessionRevoker tenantMemberSessionRevoker(AuthenticatedSessionGateway sessions) {
        return membershipId -> sessions.revokeMembershipSessions(
                new com.aiplatform.foundation.security.identity.MembershipId(membershipId.value()));
    }

    @Bean
    @ConditionalOnMissingBean
    PersonSessionFamilyRevoker personSessionFamilyRevoker(
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions) {
        return subjectId -> {
            var securitySubjectId = new com.aiplatform.foundation.security.identity.SubjectId(subjectId.value());
            tenantSessions.revokeSubjectSessions(securitySubjectId);
            platformSessions.revokeSubjectSessions(securitySubjectId);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAccountGovernanceApplicationService platformAccountGovernanceApplicationService(
            PlatformAccountStore store,
            PersonSessionFamilyRevoker sessions,
            PasswordHasher passwords) {
        return new PlatformAccountGovernanceApplicationService(store, sessions, passwords);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAccountEstablishmentApplicationService platformAccountEstablishmentApplicationService(
            PlatformAccountStore store, PasswordHasher passwords, EntityIdGenerator identifiers) {
        return new PlatformAccountEstablishmentApplicationService(store, passwords, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAccountDirectoryApplicationService platformAccountDirectoryApplicationService(
            PlatformAccountStore store) {
        return new PlatformAccountDirectoryApplicationService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformRoleBindingApplicationService platformRoleBindingApplicationService(
            PlatformRoleBindingStore store,
            PersonSessionFamilyRevoker sessions,
            EntityIdGenerator identifiers) {
        return new PlatformRoleBindingApplicationService(store, sessions, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantRoleBindingApplicationService tenantRoleBindingApplicationService(
            TenantRoleBindingStore store,
            TenantMemberSessionRevoker sessions) {
        return new TenantRoleBindingApplicationService(store, sessions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantRoleRevocationApplicationService tenantRoleRevocationApplicationService(
            TenantRoleRevocationStore store,
            TenantMemberSessionRevoker sessions) {
        return new TenantRoleRevocationApplicationService(store, sessions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleApplicationService tenantCustomRoleApplicationService(
            TenantCustomRoleStore store,
            EntityIdGenerator identifiers) {
        return new TenantCustomRoleApplicationService(store, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleBindingApplicationService tenantCustomRoleBindingApplicationService(
            TenantCustomRoleBindingStore store,
            TenantMemberSessionRevoker sessions) {
        return new TenantCustomRoleBindingApplicationService(store, sessions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleBindingRevocationApplicationService tenantCustomRoleBindingRevocationApplicationService(
            TenantCustomRoleBindingRevocationStore store,
            TenantMemberSessionRevoker sessions) {
        return new TenantCustomRoleBindingRevocationApplicationService(store, sessions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleLifecycleApplicationService tenantCustomRoleLifecycleApplicationService(
            TenantCustomRoleLifecycleStore store,
            TenantMemberSessionRevoker sessions) {
        return new TenantCustomRoleLifecycleApplicationService(store, sessions, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleDirectoryApplicationService tenantCustomRoleDirectoryApplicationService(
            TenantCustomRoleDirectory directory) {
        return new TenantCustomRoleDirectoryApplicationService(directory);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberStatusApplicationService tenantMemberStatusApplicationService(
            TenantMemberStatusStore store,
            TenantMemberSessionRevoker sessions) {
        return new TenantMemberStatusApplicationService(store, sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberDirectoryApplicationService tenantMemberDirectoryApplicationService(
            TenantMemberDirectory directory) {
        return new TenantMemberDirectoryApplicationService(directory);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberEstablishmentApplicationService tenantMemberEstablishmentApplicationService(
            TenantMemberEstablishmentStore store, EntityIdGenerator identifiers) {
        return new TenantMemberEstablishmentApplicationService(store, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleBackfillService tenantLifecycleBackfillService(
            TenantLifecycleBackfillStore store) {
        return new TenantLifecycleBackfillService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleQueryService tenantLifecycleQueryService(TenantLifecycleReadPort readPort) {
        return new TenantLifecycleQueryService(readPort);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleTransitionService tenantLifecycleTransitionService(
            TenantLifecycleTransitionStore store) {
        return new TenantLifecycleTransitionService(store, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantProvisioningService tenantProvisioningService(
            TenantProvisioningStore store,
            EntityIdGenerator identifiers) {
        return new TenantProvisioningService(store, identifiers, Clock.systemUTC());
    }
}
