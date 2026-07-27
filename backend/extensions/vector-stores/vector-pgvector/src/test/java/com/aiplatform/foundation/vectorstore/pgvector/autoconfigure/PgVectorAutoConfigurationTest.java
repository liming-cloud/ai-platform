package com.aiplatform.foundation.vectorstore.pgvector.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aiplatform.foundation.vectorstore.pgvector.PgVectorIndexStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class PgVectorAutoConfigurationTest {

    @Test
    void 显式启用且存在数据库访问能力时注册向量存储() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PgVectorAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.vector-store.pgvector.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PgVectorIndexStore.class);
                });
    }

    @Test
    void 未启用时不注册向量存储() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PgVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PgVectorIndexStore.class);
                });
    }

    @Test
    void 允许应用覆盖向量存储实现() {
        var custom = mock(PgVectorIndexStore.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PgVectorAutoConfiguration.class))
                .withPropertyValues(
                        "foundation.vector-store.pgvector.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(PgVectorIndexStore.class, () -> custom)
                .run(context -> assertThat(context.getBean(PgVectorIndexStore.class))
                        .isSameAs(custom));
    }
}
