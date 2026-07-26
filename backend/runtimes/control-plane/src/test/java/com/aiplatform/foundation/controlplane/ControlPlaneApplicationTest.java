package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ControlPlaneApplicationTest {

    @Test
    void 应在无外部基础设施时完成最小上下文启动() {
        try (var context = new SpringApplicationBuilder(ControlPlaneApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--foundation.organization-access.enabled=false",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
                                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                                + "cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate")) {
            assertTrue(context.isActive());
        }
    }
}
