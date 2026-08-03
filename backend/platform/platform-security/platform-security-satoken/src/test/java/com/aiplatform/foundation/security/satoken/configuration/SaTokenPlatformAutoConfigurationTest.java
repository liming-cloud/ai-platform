package com.aiplatform.foundation.security.satoken.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import com.aiplatform.foundation.security.satoken.web.SaTokenPlatformContextArgumentResolver;
import com.aiplatform.foundation.security.satoken.web.SaTokenTrustedContextArgumentResolver;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class SaTokenPlatformAutoConfigurationTest {

    @Test
    void 安全自动配置应装配相互独立的租户与平台会话端口() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SaTokenSecurityAutoConfiguration.class))
                .withBean(SaTokenDao.class, () -> mock(SaTokenDao.class))
                .withBean(SaTokenConfig.class, SaTokenConfig::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthenticatedSessionGateway.class);
                    assertThat(context).hasSingleBean(PlatformSessionGateway.class);
                    assertThat(context.getBean(AuthenticatedSessionGateway.class))
                            .isNotSameAs(context.getBean(PlatformSessionGateway.class));
                });
    }

    @Test
    void Web自动配置应分别注册两种令牌上下文解析器() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SaTokenWebAutoConfiguration.class))
                .withBean(AuthenticatedSessionGateway.class, () -> mock(AuthenticatedSessionGateway.class))
                .withBean(PlatformSessionGateway.class, () -> mock(PlatformSessionGateway.class))
                .withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasSingleBean(SaTokenTrustedContextArgumentResolver.class);
                    assertThat(context).hasSingleBean(SaTokenPlatformContextArgumentResolver.class);
                });
    }
}
