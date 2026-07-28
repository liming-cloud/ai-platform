package com.aiplatform.foundation.security.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentSecretEncryptionKeyProviderTest {

    @Test
    void 从版本化环境变量提供当前和历史主密钥() {
        var environment = Map.of(
                "FOUNDATION_SECRET_ENCRYPTION_KEY_V2", "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                "FOUNDATION_SECRET_ENCRYPTION_KEY_V1", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU=");
        var provider = new EnvironmentSecretEncryptionKeyProvider("V2", environment::get);

        try (var active = provider.activeKey(); var historical = provider.resolve("V1")) {
            assertEquals("V2", active.version());
            assertEquals("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=", active.key().use(String::new));
            assertEquals("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU=", historical.use(String::new));
        }
    }

    @Test
    void 缺失版本化环境变量必须失败关闭() {
        var provider = new EnvironmentSecretEncryptionKeyProvider("V1", ignored -> null);

        assertThrows(IllegalStateException.class, provider::activeKey);
    }
}
