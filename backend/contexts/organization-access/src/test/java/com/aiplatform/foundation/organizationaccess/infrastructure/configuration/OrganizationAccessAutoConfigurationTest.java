package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformAccessGrantMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.AuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.PlatformAuthenticationController;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OrganizationAccessAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OrganizationAccessPersistenceAutoConfiguration.class,
                    OrganizationAccessCoreAutoConfiguration.class,
                    OrganizationAccessWebAutoConfiguration.class))
            .withPropertyValues("foundation.organization-access.enabled=true")
            .withBean(
                    SubjectIdentityMapper.class,
                    () -> mock(SubjectIdentityMapper.class))
            .withBean(
                    TenantMembershipMapper.class,
                    () -> mock(TenantMembershipMapper.class))
            .withBean(
                    PlatformAccessGrantMapper.class,
                    () -> mock(PlatformAccessGrantMapper.class))
            .withBean(
                    TenantLifecycleBackfillMapper.class,
                    () -> mock(TenantLifecycleBackfillMapper.class))
            .withBean(
                    AuthenticatedSessionGateway.class,
                    () -> mock(AuthenticatedSessionGateway.class))
            .withBean(
                    PlatformSessionGateway.class,
                    () -> mock(PlatformSessionGateway.class))
            .withBean(EntityIdGenerator.class, () -> () -> 1001L);

    @Test
    void 启用时由上下文自行装配持久化应用服务和控制器() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PersonAuthenticationService.class);
            assertThat(context).hasSingleBean(PlatformAuthenticationService.class);
            assertThat(context).hasSingleBean(TenantLifecycleBackfillService.class);
            assertThat(context).hasSingleBean(AuthenticationController.class);
            assertThat(context).hasSingleBean(PlatformAuthenticationController.class);
        });
    }

    @Test
    void 关闭上下文时不装配任何入口和应用服务() {
        runner.withPropertyValues("foundation.organization-access.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(
                            PersonAuthenticationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantLifecycleBackfillService.class);
                    assertThat(context).doesNotHaveBean(
                            AuthenticationController.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformAuthenticationController.class);
                });
    }

    @Test
    void 缺少会话端口时启动失败() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OrganizationAccessPersistenceAutoConfiguration.class,
                        OrganizationAccessCoreAutoConfiguration.class))
                .withPropertyValues("foundation.organization-access.enabled=true")
                .withBean(
                        SubjectIdentityMapper.class,
                        () -> mock(SubjectIdentityMapper.class))
                .withBean(
                        TenantMembershipMapper.class,
                        () -> mock(TenantMembershipMapper.class))
                .run(context -> assertThat(context).hasFailed());
    }
}
