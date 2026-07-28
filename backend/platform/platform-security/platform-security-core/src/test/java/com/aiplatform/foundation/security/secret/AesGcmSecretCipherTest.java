package com.aiplatform.foundation.security.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

    @Test
    void 加密结果可由相同主密钥解密且不保留明文() {
        var cipher = new AesGcmSecretCipher();
        var keys = keyProvider();
        try (var plaintext = SecretValue.copyOf("model-api-key".toCharArray())) {
            var encrypted = cipher.encrypt(plaintext, keys);
            try (var decrypted = cipher.decrypt(encrypted, keys)) {
                assertEquals("AES-256-GCM", encrypted.algorithm());
                assertEquals("v1", encrypted.masterKeyVersion());
                assertEquals(12, encrypted.dataKeyNonce().length);
                assertEquals(12, encrypted.ciphertextNonce().length);
                assertEquals("model-api-key", decrypted.use(String::new));
            }
        }
    }

    @Test
    void 被篡改的密文必须失败关闭() {
        var cipher = new AesGcmSecretCipher();
        var keys = keyProvider();
        try (var plaintext = SecretValue.copyOf("model-api-key".toCharArray())) {
            var encrypted = cipher.encrypt(plaintext, keys);
            var tampered = Arrays.copyOf(encrypted.ciphertext(), encrypted.ciphertext().length);
            tampered[0] ^= 1;
            var modified = new AesGcmSecretCipher.EncryptedSecret(
                    encrypted.algorithm(),
                    encrypted.masterKeyVersion(),
                    encrypted.dataKeyNonce(),
                    encrypted.encryptedDataKey(),
                    encrypted.ciphertextNonce(),
                    tampered);
            assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(modified, keys));
        }
    }

    @Test
    void 被篡改的数据密钥封装材料必须失败关闭() {
        var cipher = new AesGcmSecretCipher();
        var keys = keyProvider();
        try (var plaintext = SecretValue.copyOf("model-api-key".toCharArray())) {
            var encrypted = cipher.encrypt(plaintext, keys);
            var tampered = Arrays.copyOf(encrypted.encryptedDataKey(), encrypted.encryptedDataKey().length);
            tampered[0] ^= 1;
            var modified = new AesGcmSecretCipher.EncryptedSecret(
                    encrypted.algorithm(),
                    encrypted.masterKeyVersion(),
                    encrypted.dataKeyNonce(),
                    tampered,
                    encrypted.ciphertextNonce(),
                    encrypted.ciphertext());
            assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(modified, keys));
        }
    }

    @Test
    void 每次加密必须使用新的数据密钥封装材料() {
        var cipher = new AesGcmSecretCipher();
        var keys = keyProvider();
        try (var plaintext = SecretValue.copyOf("model-api-key".toCharArray())) {
            var first = cipher.encrypt(plaintext, keys);
            var second = cipher.encrypt(plaintext, keys);
            assertFalse(Arrays.equals(first.dataKeyNonce(), second.dataKeyNonce()));
            assertFalse(Arrays.equals(first.encryptedDataKey(), second.encryptedDataKey()));
        }
    }

    private static SecretEncryptionKeyProvider keyProvider() {
        return new SecretEncryptionKeyProvider() {
            @Override
            public ActiveKey activeKey() {
                return new ActiveKey("v1", key());
            }

            @Override
            public SecretValue resolve(String keyVersion) {
                assertEquals("v1", keyVersion);
                return key();
            }

            private SecretValue key() {
                return SecretValue.copyOf("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=".toCharArray());
            }
        };
    }
}
