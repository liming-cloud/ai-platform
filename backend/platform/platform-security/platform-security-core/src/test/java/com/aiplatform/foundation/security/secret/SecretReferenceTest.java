package com.aiplatform.foundation.security.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SecretReferenceTest {

    @Test
    void 应接受规范密钥引用并保持值语义() {
        var reference = new SecretReference("secret://vault/tenant/provider");

        assertEquals("secret://vault/tenant/provider", reference.value());
        assertEquals(reference, new SecretReference("secret://vault/tenant/provider"));
    }

    @Test
    void 应拒绝非密钥协议和包含空白的引用() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SecretReference("https://vault.example/tenant/provider"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SecretReference("secret://vault/tenant provider"));
    }
}
