package com.aiplatform.foundation.security.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SecretValueTest {

    @Test
    void 应在使用后立即清零临时副本并隐藏字符串表达() {
        var observed = new AtomicReference<char[]>();

        try (var secret = SecretValue.copyOf("sensitive-value".toCharArray())) {
            var length = secret.use(chars -> {
                observed.set(chars);
                return chars.length;
            });

            assertEquals(15, length);
            assertEquals("[已脱敏]", secret.toString());
        }

        assertTrue(allCleared(observed.get()));
    }

    @Test
    void 应拒绝关闭后再次使用() {
        var secret = SecretValue.copyOf("temporary".toCharArray());
        secret.close();

        assertThrows(IllegalStateException.class, () -> secret.use(chars -> chars.length));
    }

    private static boolean allCleared(char[] value) {
        for (var character : value) {
            if (character != '\0') {
                return false;
            }
        }
        return true;
    }
}
