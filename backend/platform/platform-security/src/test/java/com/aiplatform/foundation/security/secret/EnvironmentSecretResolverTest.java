package com.aiplatform.foundation.security.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentSecretResolverTest {

    @Test
    void 只允许通过稳定引用读取指定环境变量() {
        var resolver = new EnvironmentSecretResolver(
                Map.of("MODEL_API_KEY", "secret-value")::get);

        try (var secret = resolver.resolve(
                new SecretReference("secret://environment/MODEL_API_KEY"))) {
            assertEquals("secret-value", secret.use(String::new));
        }
    }

    @Test
    void 拒绝其他存储和缺失变量() {
        var resolver = new EnvironmentSecretResolver(name -> null);

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(new SecretReference("secret://vault/model-key")));
        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(
                        new SecretReference("secret://environment/MISSING_KEY")));
    }
}
