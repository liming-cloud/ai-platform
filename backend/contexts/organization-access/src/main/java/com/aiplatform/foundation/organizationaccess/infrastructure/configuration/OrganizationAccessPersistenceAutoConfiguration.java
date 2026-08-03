package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformAccessGrantMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.repository.PostgresPlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.repository.PostgresSubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.repository.PostgresTenantMembershipRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantLifecycleBackfillStore;
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
    SubjectIdentityMapper.class,
    TenantMembershipMapper.class,
    TenantLifecycleBackfillMapper.class
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
    TenantLifecycleBackfillStore tenantLifecycleBackfillStore(
            TenantLifecycleBackfillMapper mapper,
            EntityIdGenerator identifiers) {
        return new PostgresTenantLifecycleBackfillStore(mapper, identifiers);
    }

    @Bean
    @ConditionalOnMissingBean
    TenantProvisioningStore tenantProvisioningStore(
            TenantProvisioningMapper mapper,
            EntityIdGenerator identifiers) {
        return new PostgresTenantProvisioningStore(mapper, identifiers);
    }
}
