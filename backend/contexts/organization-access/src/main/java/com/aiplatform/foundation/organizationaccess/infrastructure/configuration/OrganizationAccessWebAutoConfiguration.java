package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.AuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.BrowserAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.PlatformAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantLifecycleController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantQuotaSettingsController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantFeatureSettingsController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.PlatformTenantAdministratorController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.TenantRoleBindingController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.PlatformAccountGovernanceController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.PlatformAccountCandidateController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.PlatformAccountEstablishmentController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller.PlatformRoleBindingController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller.TenantMemberStatusController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.member.controller.TenantMemberCandidateController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * 在组织权限能力启用时注册 HTTP 入口，缺失必需依赖时由启动校验直接失败。
 */
@AutoConfiguration(after = OrganizationAccessCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = "foundation.organization-access.enabled",
        havingValue = "true")
@Import({
    AuthenticationController.class,
    BrowserAuthenticationController.class,
    PlatformAuthenticationController.class,
    TenantLifecycleController.class,
    TenantQuotaSettingsController.class,
    TenantFeatureSettingsController.class,
    PlatformTenantAdministratorController.class,
    TenantRoleBindingController.class,
    PlatformAccountGovernanceController.class,
    PlatformAccountCandidateController.class,
    PlatformAccountEstablishmentController.class,
    PlatformRoleBindingController.class,
    TenantMemberCandidateController.class,
    TenantMemberStatusController.class
})
public class OrganizationAccessWebAutoConfiguration {
}
