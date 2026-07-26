package com.aiplatform.foundation.ingestionworker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class IngestionWorkerApplicationTest {

    @Test
    void 应在无外部基础设施时完成最小上下文启动() {
        try (var context = new SpringApplicationBuilder(IngestionWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.main.banner-mode=off")) {
            assertTrue(context.isActive());
        }
    }
}
