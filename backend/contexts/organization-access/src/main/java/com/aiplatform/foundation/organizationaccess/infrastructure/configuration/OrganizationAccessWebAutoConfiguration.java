package com.aiplatform.foundation.organizationaccess.infrastructure.configuration;

import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.AuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.BrowserAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller.PlatformAuthenticationController;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller.TenantLifecycleController;
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
    TenantLifecycleController.class
})
public class OrganizationAccessWebAutoConfiguration {
}
