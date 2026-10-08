package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordHasher;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionCapabilityService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionTerminationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.TenantLoginPolicyEnforcer;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.candidate.PlatformAccountCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.candidate.PlatformAccountCandidateApplicationService;
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
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorCredentialService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorQueryService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberStatusApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberCandidateApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantQuotaSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLoginSecuritySettingsService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantWorkspaceContextQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantQuotaSettingsService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureSettingsService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureAvailabilityService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
    com.aiplatform.foundation.organizationaccess.application.accesspolicy.AccessPolicyService accessPolicyService(
            com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService accounts,
            com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore members,
            com.aiplatform.foundation.organizationaccess.application.accesspolicy.AccessPolicyStore policies,
            PlatformAccountStore identities, TenantMemberStatusStore memberships, TenantFeatureSettingsStore ceilings,
            com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup assistants,
            com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeSpaceNameLookup knowledge,
            TenantMemberSessionRevoker sessions, EntityIdGenerator identifiers) {
        return new com.aiplatform.foundation.organizationaccess.application.accesspolicy.AccessPolicyService(accounts, members, policies,
                identities, memberships, ceilings, assistants, knowledge, sessions, identifiers, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    TenantFeatureAvailabilityService tenantFeatureAvailabilityService(TenantFeatureSettingsStore store) {
        return new TenantFeatureAvailabilityService(store);
    }

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
            TenantLoginPolicyEnforcer policy,
            Pbkdf2PasswordAdapter passwords) {
        var dummyPassword = "不存在成员的等价验证口令".toCharArray();
        try {
            return new PersonAuthenticationService(
                    identities,
                    memberships,
                    passwords,
                    sessions,
                    policy,
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
    TenantLoginPolicyEnforcer tenantLoginPolicyEnforcer(TenantLoginSecurityPolicyStore policies,
            TenantLoginAttemptStore attempts) {
        return new TenantLoginPolicyEnforcer(policies, attempts, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    BrowserAuthenticationService browserAuthenticationService(
            SubjectIdentityRepository identities,
            TenantMembershipRepository memberships,
            PlatformRoleBindingRepository bindings,
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions,
            TenantLoginPolicyEnforcer policy,
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
                    policy,
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
            TenantFeatureSettingsStore featureSettings,
            TenantLoginSecurityPolicyStore loginPolicies,
            com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade memberAccess,
            @Value("${foundation.security.session-timeout:PT1H}") String sessionTimeout) {
        return new BrowserSessionCapabilityService(
                tenantSessions, platformSessions, Duration.parse(sessionTimeout), featureSettings, loginPolicies, memberAccess);
    }

    @Bean
    @ConditionalOnMissingBean(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class)
    com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade memberAccessFacade(
            com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts facts,
            TenantFeatureSettingsStore ceilings,
            com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup versions) {
        return new com.aiplatform.foundation.organizationaccess.application.member.service.MemberEffectiveAccessService(facts, ceilings, versions);
    }

    /** 迁移中登记的普通资格在接收新请求前撤销旧会话；失败阻止启动，不补授权。 */
    @Bean
    org.springframework.boot.ApplicationRunner memberAccessCutover(
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccountMapper mapper,
            TenantMemberSessionRevoker sessions,
            org.springframework.transaction.PlatformTransactionManager transactions) {
        return args -> new com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.MemberAccessCutover(
                mapper, sessions, new org.springframework.transaction.support.TransactionTemplate(transactions)).run();
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
            PlatformAccountStore store, PlatformRoleBindingStore bindings,
            PasswordHasher passwords, EntityIdGenerator identifiers) {
        return new PlatformAccountEstablishmentApplicationService(store, bindings, passwords, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAccountDirectoryApplicationService platformAccountDirectoryApplicationService(
            PlatformAccountStore store) {
        return new PlatformAccountDirectoryApplicationService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PlatformAccountCandidateDirectory.class)
    PlatformAccountCandidateApplicationService platformAccountCandidateApplicationService(
            PlatformAccountCandidateDirectory directory) {
        return new PlatformAccountCandidateApplicationService(directory);
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
    com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService memberAccountApplicationService(
            com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore store,
            PlatformAccountStore identities, TenantMemberEstablishmentApplicationService establishment,
            TenantMemberStatusApplicationService statuses, TenantMemberStatusStore membershipLocks,
            TenantFeatureSettingsStore ceilings,
            com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService assistants,
            com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup assistantAuthorization,
            PasswordHasher hasher, com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier verifier,
            PersonSessionFamilyRevoker sessions, EntityIdGenerator identifiers) {
        return new com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService(
                store, identities, establishment, statuses, membershipLocks, ceilings, assistants, assistantAuthorization,
                hasher, verifier, sessions, identifiers, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TenantMemberCandidateDirectory.class)
    TenantMemberCandidateApplicationService tenantMemberCandidateApplicationService(
            TenantMemberCandidateDirectory directory) {
        return new TenantMemberCandidateApplicationService(directory);
    }

    @Bean
    @ConditionalOnMissingBean
    com.aiplatform.foundation.organizationaccess.application.member.service.MemberFeatureSettingsService memberFeatureSettingsService(
            com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService accounts,
            com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore store,
            PlatformAccountStore identities, TenantMemberStatusStore memberships,
            TenantFeatureSettingsStore ceilings, TenantMemberSessionRevoker sessions) {
        return new com.aiplatform.foundation.organizationaccess.application.member.service.MemberFeatureSettingsService(
                accounts, store, identities, memberships, ceilings, sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    com.aiplatform.foundation.organizationaccess.application.member.service.MemberAssistantSettingsService memberAssistantSettingsService(
            com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService accounts,
            com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore store,
            PlatformAccountStore identities, TenantMemberStatusStore memberships,
            com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantAuthorizationLookup assistants,
            TenantMemberSessionRevoker sessions) {
        return new com.aiplatform.foundation.organizationaccess.application.member.service.MemberAssistantSettingsService(
                accounts, store, identities, memberships, assistants, sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberEstablishmentApplicationService tenantMemberEstablishmentApplicationService(
            TenantMemberEstablishmentStore store, EntityIdGenerator identifiers) {
        return new TenantMemberEstablishmentApplicationService(store, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformTenantAdministratorApplicationService platformTenantAdministratorApplicationService(
            PlatformTenantAdministratorStore store,
            PersonSessionFamilyRevoker sessions,
            EntityIdGenerator identifiers) {
        return new PlatformTenantAdministratorApplicationService(
                store, sessions, identifiers, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformTenantAdministratorQueryService platformTenantAdministratorQueryService(
            PlatformTenantAdministratorDirectory directory) {
        return new PlatformTenantAdministratorQueryService(directory);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformTenantAdministratorCredentialService platformTenantAdministratorCredentialService(
            PlatformTenantAdministratorStore administrators,
            PlatformAccountStore accounts,
            PersonSessionFamilyRevoker sessions,
            PasswordHasher passwords) {
        return new PlatformTenantAdministratorCredentialService(administrators, accounts, sessions, passwords);
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
    TenantWorkspaceContextQueryService tenantWorkspaceContextQueryService(
            com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService accounts,
            TenantLifecycleReadPort readPort) {
        return new TenantWorkspaceContextQueryService(accounts, readPort);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "foundation.knowledge-data.persistence.enabled", havingValue = "true")
    TenantQuotaSettingsService tenantQuotaSettingsService(TenantQuotaSettingsStore store,
            com.aiplatform.foundation.knowledgedata.application.knowledge.facade.KnowledgeQuotaUsage usage) {
        return new TenantQuotaSettingsService(store, usage);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantSessionRevoker tenantSessionRevoker(TenantMembershipMapper memberships,
            AuthenticatedSessionGateway sessions) {
        return tenantId -> {
            var ids = memberships.selectObjs(new LambdaQueryWrapper<TenantMembershipEntity>()
                    .select(TenantMembershipEntity::getMembershipId)
                    .eq(TenantMembershipEntity::getTenantId, tenantId.value()));
            for (var id : ids) {
                sessions.revokeMembershipSessions(new com.aiplatform.foundation.security.identity.MembershipId(
                        ((Number) id).longValue()));
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    TenantFeatureSettingsService tenantFeatureSettingsService(TenantFeatureSettingsStore store,
            TenantSessionRevoker sessions) {
        return new TenantFeatureSettingsService(store, sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLoginSecuritySettingsService tenantLoginSecuritySettingsService(
            TenantLoginSecurityPolicyStore store, TenantSessionRevoker sessions) {
        return new TenantLoginSecuritySettingsService(store, sessions);
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
            EntityIdGenerator identifiers,
            PasswordHasher passwords) {
        return new TenantProvisioningService(store, identifiers, passwords, Clock.systemUTC());
    }
}
