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
            .withBean(TenantFeatureSettingsMapper.class, () -> mock(TenantFeatureSettingsMapper.class));

    @Test
    void 仅显式开启只读功能事实时装配检查器() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TenantFeatureAvailability.class));
        runner.withPropertyValues("foundation.organization-access.feature-read.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TenantFeatureAvailability.class);
                });
    }
}
