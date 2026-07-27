package com.aiplatform.foundation.platform.web.role;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 在可执行应用启动时校验控制面、查询和摄取角色互斥。
 */
@AutoConfiguration
@ConditionalOnProperty(
        name = "foundation.application-role-validation.enabled",
        havingValue = "true")
@EnableConfigurationProperties(ApplicationRoleProperties.class)
public class ApplicationRoleAutoConfiguration {

    @Bean
    ApplicationRoleValidator applicationRoleValidator(
            ApplicationRoleProperties properties) {
        return new ApplicationRoleValidator(properties);
    }
}
