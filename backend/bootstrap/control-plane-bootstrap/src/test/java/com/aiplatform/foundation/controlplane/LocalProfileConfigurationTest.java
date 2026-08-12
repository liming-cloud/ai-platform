package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalProfileConfigurationTest {

    @Test
    void 本地研发profile只加载空库认证初始化且不得加载演示身份种子() throws Exception {
        try (var input = getClass().getResourceAsStream("/application-local.yml")) {
            assertNotNull(input, "本地研发 profile 必须存在");
            var configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(configuration.contains("db/demo/"));
            assertTrue(configuration.contains("classpath:db/local/organization-access"));
        }
    }

    @Test
    void 本地控制面必须传入模型凭据开发密钥版本() throws Exception {
        var compose = Files.readString(
                Path.of("../../../deploy/compose/compose.yml"), StandardCharsets.UTF_8);
        assertTrue(compose.contains("FOUNDATION_MODEL_CREDENTIALS_LOCAL_DEVELOPMENT_KEY_VERSION"));
        assertTrue(compose.contains("FOUNDATION_SECRET_ENCRYPTION_KEY_M2_V1"));
    }
}
