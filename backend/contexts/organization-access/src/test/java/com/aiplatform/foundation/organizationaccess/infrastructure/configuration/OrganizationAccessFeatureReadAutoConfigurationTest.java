package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantFeatureSettingsMapper;
import com.aiplatform.foundation.security.identity.TenantFeatureAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OrganizationAccessFeatureReadAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrganizationAccessFeatureReadAutoConfiguration.class))
            .withBean(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper.class,
                    () -> mock(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper.class))
            .withBean(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper.class,
                    () -> mock(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccessMapper.class))
            .withBean(com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup.class,
                    () -> mock(com.aiplatform.foundation.assistantdelivery.application.assistant.facade.PublishedAssistantVersionLookup.class))
            .withBean(TenantFeatureSettingsMapper.class, () -> mock(TenantFeatureSettingsMapper.class));

    @Test
    void 仅显式开启只读功能事实时装配检查器() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(TenantFeatureAvailability.class);
            assertThat(context).doesNotHaveBean(com.aiplatform.foundation.security.identity.TenantActivityFacts.class);
        });
        runner.withPropertyValues("foundation.organization-access.feature-read.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TenantFeatureAvailability.class);
                    assertThat(context).hasSingleBean(com.aiplatform.foundation.security.identity.TenantActivityFacts.class);
                    assertThat(context).hasSingleBean(com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade.class);
                    assertThat(context).doesNotHaveBean(com.aiplatform.foundation.organizationaccess.application.member.port.out.MemberAccountStore.class);
                    assertThat(context).doesNotHaveBean(com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.MemberAccountMapper.class);
                    assertThat(context).doesNotHaveBean(com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller.TenantMemberStatusController.class);
                });
    }
}
