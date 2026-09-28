package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureAvailabilityService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantFeatureSettingsMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantFeatureSettingsStore;
import com.aiplatform.foundation.security.identity.TenantFeatureAvailability;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 只读查询进程仅获取功能上限，不开启组织身份与管理控制面。 */
@AutoConfiguration
@ConditionalOnProperty(name = "foundation.organization-access.feature-read.enabled", havingValue = "true")
public class OrganizationAccessFeatureReadAutoConfiguration {
    /** 只注册功能配置 Mapper，避免扫描同目录的账户与生命周期映射器。 */
    @Bean
    @ConditionalOnMissingBean(TenantFeatureSettingsMapper.class)
    MapperFactoryBean<TenantFeatureSettingsMapper> tenantFeatureSettingsMapper(SqlSessionFactory sessions) {
        var mapper = new MapperFactoryBean<>(TenantFeatureSettingsMapper.class);
        mapper.setSqlSessionFactory(sessions);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean(TenantFeatureSettingsStore.class)
    TenantFeatureSettingsStore tenantFeatureSettingsStore(TenantFeatureSettingsMapper mapper) {
        return new PostgresTenantFeatureSettingsStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TenantFeatureAvailability.class)
    TenantFeatureAvailability tenantFeatureAvailability(TenantFeatureSettingsStore store) {
        return new TenantFeatureAvailabilityService(store);
    }
}
