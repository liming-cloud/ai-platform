package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class TenantCustomRoleDirectoryApplicationServiceProxyTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TransactionalDirectoryConfiguration.class);

    @Test
    void 启用事务时自定义角色目录读取服务可以完成代理装配() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TenantCustomRoleDirectoryApplicationService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionalDirectoryConfiguration {

        @Bean
        TenantCustomRoleDirectory tenantCustomRoleDirectory() {
            return (tenantId, page) -> null;
        }

        @Bean
        TenantCustomRoleDirectoryApplicationService tenantCustomRoleDirectoryApplicationService(
                TenantCustomRoleDirectory directory) {
            return new TenantCustomRoleDirectoryApplicationService(directory);
        }
    }
}
