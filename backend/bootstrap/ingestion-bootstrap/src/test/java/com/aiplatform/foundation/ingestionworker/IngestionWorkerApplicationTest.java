package com.aiplatform.foundation.ingestionworker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller.ProviderConnectionController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class IngestionWorkerApplicationTest {

    @Test
    void 应在无外部基础设施时完成最小上下文启动() {
        try (var context = new SpringApplicationBuilder(IngestionWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.main.banner-mode=off",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.jdbc.autoconfigure."
                                + "DataSourceAutoConfiguration,"
                                + "org.mybatis.spring.boot.autoconfigure."
                                + "MybatisAutoConfiguration")) {
            assertTrue(context.isActive());
            assertTrue(context.getBeansOfType(ProviderConnectionController.class).isEmpty());
        }
    }

    @Test
    void Web运行器不得装配控制平面模型目录接口() {
        try (var context = new SpringApplicationBuilder(IngestionWorkerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--spring.main.banner-mode=off",
                        "--server.port=0",
                        "--spring.autoconfigure.exclude="
                                + "org.springframework.boot.jdbc.autoconfigure."
                                + "DataSourceAutoConfiguration,"
                                + "org.mybatis.spring.boot.autoconfigure."
                                + "MybatisAutoConfiguration")) {
            assertTrue(context.isActive());
            assertTrue(context.getBeansOfType(ProviderConnectionController.class).isEmpty());
        }
    }

    @Test
    void 本地摄取工作器必须接收受控模型凭据开发密钥() throws Exception {
        var compose = Files.readString(
                Path.of("../../../deploy/compose/compose.yml"), StandardCharsets.UTF_8);
        var ingestionWorker = compose.substring(
                compose.indexOf("  ingestion-worker:"),
                compose.indexOf("  console-web:"));

        assertTrue(ingestionWorker.contains(
                "FOUNDATION_MODEL_CREDENTIALS_LOCAL_DEVELOPMENT_KEY_VERSION"));
        assertTrue(ingestionWorker.contains("FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1"));
    }
}
