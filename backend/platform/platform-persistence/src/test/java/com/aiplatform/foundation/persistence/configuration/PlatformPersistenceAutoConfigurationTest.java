package com.aiplatform.foundation.persistence.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.persistence.identifier.MybatisPlusSnowflakeIdGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PlatformPersistenceAutoConfiguration.class))
            .withPropertyValues(
                    "foundation.persistence.snowflake.worker-id=2",
                    "foundation.persistence.snowflake.datacenter-id=5");

    @Test
    void 应注册同一个雪花生成器作为领域和MyBatisPlus端口() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var concrete = context.getBean(MybatisPlusSnowflakeIdGenerator.class);
            assertThat(context.getBean(EntityIdGenerator.class)).isSameAs(concrete);
            assertThat(context.getBean(IdentifierGenerator.class)).isSameAs(concrete);
        });
    }

    @Test
    void 应按固定顺序注册基础持久化拦截器() {
        runner.run(context -> {
            var interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(interceptor.getInterceptors())
                    .extracting(item -> item.getClass().getSimpleName())
                    .containsExactly(
                            OptimisticLockerInnerInterceptor.class.getSimpleName(),
                            BlockAttackInnerInterceptor.class.getSimpleName(),
                            PaginationInnerInterceptor.class.getSimpleName());
            var pagination = (PaginationInnerInterceptor)
                    interceptor.getInterceptors().getLast();
            assertThat(pagination.getDbType()).isEqualTo(
                    com.baomidou.mybatisplus.annotation.DbType.POSTGRE_SQL);
            assertThat(pagination.getMaxLimit()).isEqualTo(100L);
            assertThat(pagination.isOverflow()).isFalse();
        });
    }

    @Test
    void 缺少显式节点编号时必须启动失败() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PlatformPersistenceAutoConfiguration.class))
                .run(context -> assertThat(context).hasFailed());
    }
}
