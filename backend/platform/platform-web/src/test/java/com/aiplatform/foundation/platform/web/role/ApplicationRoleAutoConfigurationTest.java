package com.aiplatform.foundation.platform.web.role;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationRoleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            ApplicationRoleAutoConfiguration.class))
                    .withPropertyValues(
                            "foundation.application-role-validation.enabled=true");

    @Test
    void 单一角色启用时启动成功() {
        contextRunner
                .withPropertyValues("foundation.query.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(ApplicationRoleValidator.class);
                });
    }

    @Test
    void 没有角色启用时启动失败() {
        contextRunner.run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void 多个角色启用时启动失败() {
        contextRunner
                .withPropertyValues(
                        "foundation.control-plane.enabled=true",
                        "foundation.query.enabled=true")
                .run(context ->
                        assertThat(context).hasFailed());
    }

    @Test
    void 校验关闭时不注册校验器() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ApplicationRoleAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.application-role-validation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(ApplicationRoleValidator.class);
                });
    }
}
