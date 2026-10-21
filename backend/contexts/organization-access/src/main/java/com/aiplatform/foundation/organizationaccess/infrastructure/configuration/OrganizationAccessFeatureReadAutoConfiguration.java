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

/** 只读查询进程获取当前功能、资格和助手授权，不开启身份与管理控制面。 */
@AutoConfiguration
@ConditionalOnProperty(name = "foundation.organization-access.feature-read.enabled", havingValue = "true")
public class OrganizationAccessFeatureReadAutoConfiguration {
    /** 供应新使用复核当前活动状态，仅注册生命周期读取 Mapper。 */
    @Bean
    @ConditionalOnMissingBean(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper.class)
    MapperFactoryBean<com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper> tenantLifecycleReadMapper(SqlSessionFactory sessions) {
        var mapper = new MapperFactoryBean<>(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper.class);
        mapper.setSqlSessionFactory(sessions);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean(com.aiplatform.foundation.security.identity.TenantActivityFacts.class)
    com.aiplatform.foundation.security.identity.TenantActivityFacts tenantActivityFacts(
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper mapper) {
        return new com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository.PostgresTenantActivityFacts(mapper);
    }

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
    /** 仅注册无写入成员 Mapper，不扫描账户管理映射器。 */
    @Bean
    @ConditionalOnMissingBean(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper.class)
    MapperFactoryBean<com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper> memberAccessMapper(SqlSessionFactory sessions) {
        var mapper = new MapperFactoryBean<>(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper.class);
        mapper.setSqlSessionFactory(sessions);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean(com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts.class)
    com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts memberAccessFacts(
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper mapper) {
        return new com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository.PostgresMemberAccessFacts(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class)
    com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade memberAccessFacade(
            com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccessFacts facts,
            TenantFeatureSettingsStore ceilings,
            com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup versions) {
        return new com.aiplatform.foundation.organizationaccess.application.member.service.MemberEffectiveAccessService(facts, ceilings, versions);
    }
}
