package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformAccessGrantMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresPlatformAccessGrantRepository;
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
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository.PostgresSubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.repository.PostgresTenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberStatusMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberDirectoryMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberDirectory;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberEstablishmentMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresTenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleTransitionMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleReadAdapter;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantProvisioningStore;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 注册组织权限持久化 Mapper 和领域仓储实现。
 */
@AutoConfiguration
@ConditionalOnProperty(
        name = "foundation.organization-access.enabled",
        havingValue = "true")
@MapperScan(basePackageClasses = {
    PlatformAccessGrantMapper.class,
    TenantCustomRoleMapper.class,
    TenantCustomRoleBindingMapper.class,
    TenantCustomRoleLifecycleMapper.class,
    TenantCustomRoleDirectoryMapper.class,
    TenantRoleBindingMapper.class,
    SubjectIdentityMapper.class,
    TenantMembershipMapper.class,
    TenantMemberStatusMapper.class,
    TenantMemberDirectoryMapper.class,
    TenantMemberEstablishmentMapper.class,
    TenantLifecycleBackfillMapper.class,
    TenantLifecycleReadMapper.class,
    TenantLifecycleTransitionMapper.class
})
public class OrganizationAccessPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SubjectIdentityRepository subjectIdentityRepository(SubjectIdentityMapper mapper) {
        return new PostgresSubjectIdentityRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAccessGrantRepository platformAccessGrantRepository(
            PlatformAccessGrantMapper mapper) {
        return new PostgresPlatformAccessGrantRepository(mapper);
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
    TenantMemberEstablishmentStore tenantMemberEstablishmentStore(TenantMemberEstablishmentMapper mapper) {
        return new PostgresTenantMemberEstablishmentStore(mapper);
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
    TenantLifecycleReadPort tenantLifecycleReadPort(TenantLifecycleReadMapper mapper) {
        return new PostgresTenantLifecycleReadAdapter(mapper);
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
            EntityIdGenerator identifiers) {
        return new PostgresTenantProvisioningStore(mapper, identifiers);
    }
}
