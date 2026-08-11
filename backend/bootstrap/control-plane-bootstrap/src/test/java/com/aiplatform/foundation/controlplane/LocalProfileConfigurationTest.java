package com.aiplatform.foundation.controlplane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LocalProfileConfigurationTest {

    @Test
    void 本地研发profile不得加载演示身份种子() throws Exception {
        try (var input = getClass().getResourceAsStream("/application-local.yml")) {
            assertNotNull(input, "本地研发 profile 必须存在");
            var configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(configuration.contains("db/demo/"));
        }
    }
}
