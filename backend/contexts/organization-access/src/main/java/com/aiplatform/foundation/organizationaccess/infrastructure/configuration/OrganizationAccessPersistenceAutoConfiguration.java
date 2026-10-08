package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformRoleBindingRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantQuotaSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.TenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.candidate.PlatformAccountCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.application.operations.facade.OrganizationOperationsFactSource;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresPlatformRoleBindingRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresPlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleLifecycleMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleDirectoryMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantCustomRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantCustomRoleBindingRevocationStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresTenantCustomRoleDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository.PostgresPlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository.JdbcPlatformAccountCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository.PostgresSubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.repository.PostgresTenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberStatusMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresPlatformTenantAdministratorDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresPlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberCandidateMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberCandidateDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberDirectoryMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberEstablishmentMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.operations.JdbcOrganizationOperationsFactSource;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleTransitionMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleReadAdapter;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantQuotaSettingsStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLoginAttemptStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantFeatureSettingsMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginSecurityPolicyMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLoginAttemptMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 注册组织权限持久化 Mapper 和领域仓储实现。
 */
@AutoConfiguration(after = JdbcClientAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.organization-access.enabled",
        havingValue = "true")
@MapperScan(basePackageClasses = {
    PlatformRoleBindingMapper.class,
    TenantCustomRoleMapper.class,
    TenantCustomRoleBindingMapper.class,
    TenantCustomRoleLifecycleMapper.class,
    TenantCustomRoleDirectoryMapper.class,
    TenantRoleBindingMapper.class,
    SubjectIdentityMapper.class,
    PlatformAccountMapper.class,
    TenantMembershipMapper.class,
    TenantMemberStatusMapper.class,
    TenantMemberDirectoryMapper.class,
    TenantMemberEstablishmentMapper.class,
    TenantLifecycleBackfillMapper.class,
    TenantLifecycleReadMapper.class,
    TenantLifecycleTransitionMapper.class,
    TenantLoginSecurityPolicyMapper.class,
    TenantLoginAttemptMapper.class
})
public class OrganizationAccessPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    OrganizationOperationsFactSource organizationOperationsFactSource(JdbcClient jdbc) {
        return new JdbcOrganizationOperationsFactSource(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    PlatformAccountCandidateDirectory platformAccountCandidateDirectory(JdbcClient jdbc) {
        return new JdbcPlatformAccountCandidateDirectory(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    SubjectIdentityRepository subjectIdentityRepository(SubjectIdentityMapper mapper) {
        return new PostgresSubjectIdentityRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAccountStore platformAccountStore(PlatformAccountMapper mapper) {
        return new PostgresPlatformAccountStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformRoleBindingRepository platformRoleBindingRepository(
            PlatformRoleBindingMapper mapper) {
        return new PostgresPlatformRoleBindingRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformRoleBindingStore platformRoleBindingStore(PlatformRoleBindingMapper mapper) {
        return new PostgresPlatformRoleBindingStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMembershipRepository tenantMembershipRepository(TenantMembershipMapper mapper) {
        return new PostgresTenantMembershipRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberStatusStore tenantMemberStatusStore(TenantMemberStatusMapper mapper) {
        return new PostgresTenantMemberStatusStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberDirectory tenantMemberDirectory(TenantMemberDirectoryMapper mapper) {
        return new PostgresTenantMemberDirectory(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore memberAccountStore(
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccountMapper mapper,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper facts) {
        return new com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresMemberAccountStore(mapper, facts);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberCandidateDirectory tenantMemberCandidateDirectory(TenantMemberCandidateMapper mapper) {
        return new PostgresTenantMemberCandidateDirectory(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantMemberEstablishmentStore tenantMemberEstablishmentStore(TenantMemberEstablishmentMapper mapper) {
        return new PostgresTenantMemberEstablishmentStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformTenantAdministratorStore platformTenantAdministratorStore(
            PlatformTenantAdministratorMapper mapper) {
        return new PostgresPlatformTenantAdministratorStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformTenantAdministratorDirectory platformTenantAdministratorDirectory(
            PlatformTenantAdministratorMapper mapper) {
        return new PostgresPlatformTenantAdministratorDirectory(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantRoleBindingStore tenantRoleBindingStore(TenantRoleBindingMapper mapper) {
        return new PostgresTenantRoleBindingStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantRoleRevocationStore tenantRoleRevocationStore(TenantRoleBindingMapper mapper) {
        return new PostgresTenantRoleRevocationStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleStore tenantCustomRoleStore(TenantCustomRoleMapper mapper) {
        return new PostgresTenantCustomRoleStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleDirectory tenantCustomRoleDirectory(TenantCustomRoleDirectoryMapper mapper) {
        return new PostgresTenantCustomRoleDirectory(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleBindingStore tenantCustomRoleBindingStore(
            TenantRoleBindingMapper membershipMapper,
            TenantCustomRoleBindingMapper mapper) {
        return new PostgresTenantCustomRoleBindingStore(membershipMapper, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleBindingRevocationStore tenantCustomRoleBindingRevocationStore(
            TenantRoleBindingMapper membershipMapper,
            TenantCustomRoleBindingMapper mapper) {
        return new PostgresTenantCustomRoleBindingRevocationStore(membershipMapper, mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantCustomRoleLifecycleStore tenantCustomRoleLifecycleStore(TenantCustomRoleLifecycleMapper mapper) {
        return new PostgresTenantCustomRoleLifecycleStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleBackfillStore tenantLifecycleBackfillStore(
            TenantLifecycleBackfillMapper mapper,
            EntityIdGenerator identifiers) {
        return new PostgresTenantLifecycleBackfillStore(mapper, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleReadPort tenantLifecycleReadPort(TenantLifecycleReadMapper mapper,
            TenantProvisioningMapper lifecycles,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper quotas) {
        return new PostgresTenantLifecycleReadAdapter(mapper, lifecycles, quotas);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantQuotaSettingsStore tenantQuotaSettingsStore(
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper quotas) {
        return new PostgresTenantQuotaSettingsStore(quotas);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantFeatureSettingsStore tenantFeatureSettingsStore(TenantFeatureSettingsMapper features) {
        return new PostgresTenantFeatureSettingsStore(features);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLoginSecurityPolicyStore tenantLoginSecurityPolicyStore(TenantLoginSecurityPolicyMapper mapper) {
        return new PostgresTenantLoginSecurityPolicyStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLoginAttemptStore tenantLoginAttemptStore(TenantLoginAttemptMapper mapper) {
        return new PostgresTenantLoginAttemptStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantLifecycleTransitionStore tenantLifecycleTransitionStore(
            TenantLifecycleTransitionMapper mapper,
            EntityIdGenerator identifiers) {
        return new PostgresTenantLifecycleTransitionStore(mapper, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantProvisioningStore tenantProvisioningStore(
            TenantProvisioningMapper mapper,
            SubjectIdentityMapper identities,
            TenantMembershipMapper memberships,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantAnchorMapper anchors,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantAdministratorRoleMapper roles,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper quotas,
            TenantFeatureSettingsMapper features,
            TenantLoginSecurityPolicyMapper security,
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleOutboxMapper outbox,
            EntityIdGenerator identifiers) {
        return new PostgresTenantProvisioningStore(identities, anchors, mapper, memberships, roles, quotas,
                features, security, outbox, identifiers);
    }
}
