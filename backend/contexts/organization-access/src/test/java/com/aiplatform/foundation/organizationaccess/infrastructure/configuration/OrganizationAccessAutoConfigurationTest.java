package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionCapabilityService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionTerminationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleTransitionService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformAccessGrantMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleTransitionMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.AuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.BrowserAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.PlatformAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantLifecycleController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.TenantRoleBindingController;
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
                    TenantRoleBindingMapper.class,
                    () -> mock(TenantRoleBindingMapper.class))
            .withBean(
                    TenantLifecycleBackfillMapper.class,
                    () -> mock(TenantLifecycleBackfillMapper.class))
            .withBean(
                    TenantLifecycleReadMapper.class,
                    () -> mock(TenantLifecycleReadMapper.class))
            .withBean(
                    TenantLifecycleTransitionMapper.class,
                    () -> mock(TenantLifecycleTransitionMapper.class))
            .withBean(
                    TenantProvisioningMapper.class,
                    () -> mock(TenantProvisioningMapper.class))
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
            assertThat(context).hasSingleBean(BrowserAuthenticationService.class);
            assertThat(context).hasSingleBean(BrowserSessionCapabilityService.class);
            assertThat(context).hasSingleBean(BrowserSessionTerminationService.class);
            assertThat(context).hasSingleBean(TenantRoleBindingApplicationService.class);
            assertThat(context).hasSingleBean(TenantLifecycleBackfillService.class);
            assertThat(context).hasSingleBean(TenantLifecycleQueryService.class);
            assertThat(context).hasSingleBean(TenantLifecycleTransitionService.class);
            assertThat(context).hasSingleBean(TenantProvisioningService.class);
            assertThat(context).hasSingleBean(AuthenticationController.class);
            assertThat(context).hasSingleBean(BrowserAuthenticationController.class);
            assertThat(context).hasSingleBean(PlatformAuthenticationController.class);
            assertThat(context).hasSingleBean(TenantLifecycleController.class);
            assertThat(context).hasSingleBean(TenantRoleBindingController.class);
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
                            BrowserAuthenticationService.class);
                    assertThat(context).doesNotHaveBean(
                            BrowserSessionCapabilityService.class);
                    assertThat(context).doesNotHaveBean(
                            BrowserSessionTerminationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantRoleBindingApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantLifecycleBackfillService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantLifecycleQueryService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantLifecycleTransitionService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantProvisioningService.class);
                    assertThat(context).doesNotHaveBean(
                            AuthenticationController.class);
                    assertThat(context).doesNotHaveBean(
                            BrowserAuthenticationController.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformAuthenticationController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantLifecycleController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantRoleBindingController.class);
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
