package com.aiplatform.foundation.queryruntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class QueryRuntimeApplicationTest {

    @Test
    void 应在无外部基础设施时完成最小上下文启动() {
        try (var context = new SpringApplicationBuilder(QueryRuntimeApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--foundation.assistant-delivery.enabled=false",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.jdbc.autoconfigure."
                                + "DataSourceAutoConfiguration,"
                                + "org.mybatis.spring.boot.autoconfigure."
                                + "MybatisAutoConfiguration,"
                                + "org.springframework.boot.data.redis.autoconfigure."
                                + "DataRedisAutoConfiguration,"
                                + "cn.dev33.satoken.dao."
                                + "SaTokenDaoForRedisTemplate")) {
            assertTrue(context.isActive());
        }
    }
}
