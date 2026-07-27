package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMemberCredentialRepository;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authentication.mapper.TenantMemberCredentialMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authentication.repository.PostgresTenantMemberCredentialRepository;
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
@MapperScan(basePackageClasses = TenantMemberCredentialMapper.class)
public class OrganizationAccessPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TenantMemberCredentialRepository tenantMemberCredentialRepository(
            TenantMemberCredentialMapper mapper) {
        return new PostgresTenantMemberCredentialRepository(mapper);
    }
}
