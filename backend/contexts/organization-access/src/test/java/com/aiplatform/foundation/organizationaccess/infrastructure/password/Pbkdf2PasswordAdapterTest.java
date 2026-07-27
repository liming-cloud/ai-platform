package com.aiplatform.foundation.organizationaccess.infrastructure.password;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Pbkdf2PasswordAdapterTest {

    private static final char[] PASSWORD = "校园门店-可信密码-🔐".toCharArray();
    private static Pbkdf2PasswordAdapter adapter;
    private static String encoded;

    @BeforeAll
    static void createPasswordHash() {
        adapter = new Pbkdf2PasswordAdapter(new SecureRandom());
        encoded = adapter.encode(PASSWORD);
    }

    @Test
    void 应验证完整Unicode密码且不修改调用方缓冲区() {
        var candidate = PASSWORD.clone();

        assertTrue(adapter.matches(candidate, encoded));
        assertArrayEquals(PASSWORD, candidate);
    }

    @Test
    void 应为相同密码生成不同盐值() {
        var another = adapter.encode(PASSWORD);

        assertNotEquals(encoded, another);
        assertTrue(adapter.matches(PASSWORD, another));
    }

    @Test
    void 应拒绝错误密码和不受控摘要参数() {
        assertFalse(adapter.matches("wrong".toCharArray(), encoded));
        assertFalse(adapter.matches(PASSWORD, "pbkdf2-sha256$v1$999999999$AA$AA"));
        assertFalse(adapter.matches(PASSWORD, "pbkdf2-sha256$v1$600000$%%%$%%%"));
        assertFalse(adapter.matches(PASSWORD, "sha256$v1$600000$AA$AA"));
        assertFalse(adapter.matches(PASSWORD, "x".repeat(257)));
    }
}
