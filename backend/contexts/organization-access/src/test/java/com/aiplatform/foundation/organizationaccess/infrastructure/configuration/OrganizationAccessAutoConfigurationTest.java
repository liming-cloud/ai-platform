package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.organizationaccess.application.authentication.service.PersonAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.PlatformAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionCapabilityService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionTerminationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleRevocationApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingRevocationApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleLifecycleApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberStatusApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.TenantMemberEstablishmentApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleBackfillService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleQueryService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLifecycleTransitionService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantProvisioningService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureSettingsService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureAvailabilityService;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.PlatformRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleLifecycleMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleDirectoryMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantCustomRoleMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper.TenantRoleBindingMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.SubjectIdentityMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper.PlatformAccountMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.mapper.TenantMembershipMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberCandidateMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberStatusMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberDirectoryMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberEstablishmentMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleTransitionMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantAnchorMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantAdministratorRoleMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantQuotaMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantFeatureSettingsMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleOutboxMapper;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.AuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.BrowserAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.PlatformAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantLifecycleController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantFeatureSettingsController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.PlatformTenantAdministratorController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.TenantRoleBindingController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.PlatformRoleBindingController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.PlatformAccountEstablishmentController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.TenantCustomRoleBindingController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.TenantCustomRoleBindingRevocationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.TenantCustomRoleController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller.TenantMemberStatusController;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
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
            // MapperScan 会在轻量装配测试中注册 MapperFactoryBean；提供会话工厂以验证自动配置边界，
            // 不连接任何数据库。
            .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
            .withBean(
                    SubjectIdentityMapper.class,
                    () -> mock(SubjectIdentityMapper.class))
            .withBean(
                    PlatformAccountMapper.class,
                    () -> mock(PlatformAccountMapper.class))
            .withBean(
                    TenantMembershipMapper.class,
                    () -> mock(TenantMembershipMapper.class))
            .withBean(
                    TenantMemberCandidateMapper.class,
                    () -> mock(TenantMemberCandidateMapper.class))
            .withBean(
                    TenantMemberStatusMapper.class,
                    () -> mock(TenantMemberStatusMapper.class))
            .withBean(
                    TenantMemberDirectoryMapper.class,
                    () -> mock(TenantMemberDirectoryMapper.class))
            .withBean(
                    TenantMemberEstablishmentMapper.class,
                    () -> mock(TenantMemberEstablishmentMapper.class))
            .withBean(
                    PlatformTenantAdministratorMapper.class,
                    () -> mock(PlatformTenantAdministratorMapper.class))
            .withBean(
                    PlatformRoleBindingMapper.class,
                    () -> mock(PlatformRoleBindingMapper.class))
            .withBean(
                    TenantRoleBindingMapper.class,
                    () -> mock(TenantRoleBindingMapper.class))
            .withBean(
                    TenantCustomRoleMapper.class,
                    () -> mock(TenantCustomRoleMapper.class))
            .withBean(
                    TenantCustomRoleBindingMapper.class,
                    () -> mock(TenantCustomRoleBindingMapper.class))
            .withBean(
                    TenantCustomRoleLifecycleMapper.class,
                    () -> mock(TenantCustomRoleLifecycleMapper.class))
            .withBean(
                    TenantCustomRoleDirectoryMapper.class,
                    () -> mock(TenantCustomRoleDirectoryMapper.class))
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
            .withBean(TenantAnchorMapper.class, () -> mock(TenantAnchorMapper.class))
            .withBean(TenantAdministratorRoleMapper.class, () -> mock(TenantAdministratorRoleMapper.class))
            .withBean(TenantQuotaMapper.class, () -> mock(TenantQuotaMapper.class))
            .withBean(TenantFeatureSettingsMapper.class, () -> mock(TenantFeatureSettingsMapper.class))
            .withBean(TenantLifecycleOutboxMapper.class, () -> mock(TenantLifecycleOutboxMapper.class))
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
            assertThat(context).hasSingleBean(PlatformAccountGovernanceApplicationService.class);
            assertThat(context).hasSingleBean(PlatformAccountEstablishmentApplicationService.class);
            assertThat(context).hasSingleBean(PlatformRoleBindingApplicationService.class);
            assertThat(context).hasSingleBean(TenantRoleRevocationApplicationService.class);
            assertThat(context).hasSingleBean(TenantCustomRoleApplicationService.class);
            assertThat(context).hasSingleBean(TenantCustomRoleBindingApplicationService.class);
            assertThat(context).hasSingleBean(TenantCustomRoleBindingRevocationApplicationService.class);
            assertThat(context).hasSingleBean(TenantCustomRoleLifecycleApplicationService.class);
            assertThat(context).hasSingleBean(TenantCustomRoleDirectoryApplicationService.class);
            assertThat(context).hasSingleBean(TenantMemberStatusApplicationService.class);
            assertThat(context).hasSingleBean(TenantMemberDirectoryApplicationService.class);
            assertThat(context).hasSingleBean(TenantMemberEstablishmentApplicationService.class);
            assertThat(context).hasSingleBean(PlatformTenantAdministratorApplicationService.class);
            assertThat(context).hasSingleBean(PlatformTenantAdministratorQueryService.class);
            assertThat(context).hasSingleBean(TenantLifecycleBackfillService.class);
            assertThat(context).hasSingleBean(TenantLifecycleQueryService.class);
            assertThat(context).hasSingleBean(TenantLifecycleTransitionService.class);
            assertThat(context).hasSingleBean(TenantProvisioningService.class);
            assertThat(context).hasSingleBean(TenantFeatureSettingsService.class);
            assertThat(context).hasSingleBean(TenantFeatureAvailabilityService.class);
            assertThat(context).hasSingleBean(AuthenticationController.class);
            assertThat(context).hasSingleBean(BrowserAuthenticationController.class);
            assertThat(context).hasSingleBean(PlatformAuthenticationController.class);
            assertThat(context).hasSingleBean(TenantLifecycleController.class);
            assertThat(context).hasSingleBean(TenantFeatureSettingsController.class);
            assertThat(context).hasSingleBean(PlatformTenantAdministratorController.class);
            assertThat(context).hasSingleBean(TenantRoleBindingController.class);
            assertThat(context).hasSingleBean(PlatformRoleBindingController.class);
            assertThat(context).hasSingleBean(PlatformAccountEstablishmentController.class);
            assertThat(context).doesNotHaveBean(TenantCustomRoleController.class);
            assertThat(context).doesNotHaveBean(TenantCustomRoleBindingController.class);
            assertThat(context).doesNotHaveBean(TenantCustomRoleBindingRevocationController.class);
            assertThat(context).hasSingleBean(TenantMemberStatusController.class);
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
                            PlatformAccountGovernanceApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformAccountEstablishmentApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformRoleBindingApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantRoleRevocationApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleBindingApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleBindingRevocationApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleLifecycleApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleDirectoryApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantMemberStatusApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantMemberDirectoryApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            TenantMemberEstablishmentApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformTenantAdministratorApplicationService.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformTenantAdministratorQueryService.class);
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
                            PlatformTenantAdministratorController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantRoleBindingController.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformRoleBindingController.class);
                    assertThat(context).doesNotHaveBean(
                            PlatformAccountEstablishmentController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleBindingController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantCustomRoleBindingRevocationController.class);
                    assertThat(context).doesNotHaveBean(
                            TenantMemberStatusController.class);
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
