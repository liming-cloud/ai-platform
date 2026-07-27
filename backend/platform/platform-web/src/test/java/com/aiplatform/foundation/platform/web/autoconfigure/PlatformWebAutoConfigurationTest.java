package com.aiplatform.foundation.platform.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.platform.web.error.FoundationExceptionHandler;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformWebAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformWebAutoConfiguration.class));

    @Test
    void 存在标识生成器时注册统一异常处理器() {
        runner.withBean(EntityIdGenerator.class, () -> () -> 1001L)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            FoundationExceptionHandler.class);
                });
    }

    @Test
    void 缺少追踪标识生成器时启动失败() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 允许应用覆盖异常处理器() {
        var custom = new FoundationExceptionHandler(() -> 2001L);

        runner.withBean(FoundationExceptionHandler.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FoundationExceptionHandler.class))
                            .isSameAs(custom);
                });
    }
}
