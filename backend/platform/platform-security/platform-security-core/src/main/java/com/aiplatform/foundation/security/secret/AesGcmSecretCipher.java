package com.aiplatform.foundation.security.secret;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 使用随机数据密钥和 AES-GCM 封装短生命周期凭据，密文篡改时必须失败关闭。
 */
public final class AesGcmSecretCipher {

    private static final String ALGORITHM = "AES-256-GCM";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();

    public EncryptedSecret encrypt(SecretValue plaintext, SecretEncryptionKeyProvider keyProvider) {
        Objects.requireNonNull(plaintext, "明文凭据不能为空");
        Objects.requireNonNull(keyProvider, "主密钥提供者不能为空");
        try (var activeKey = keyProvider.activeKey()) {
            return plaintext.use(chars -> activeKey.key().use(keyChars -> encrypt(chars, keyChars, activeKey.version())));
        }
    }

    public SecretValue decrypt(EncryptedSecret encrypted, SecretEncryptionKeyProvider keyProvider) {
        Objects.requireNonNull(encrypted, "加密凭据不能为空");
        Objects.requireNonNull(keyProvider, "主密钥提供者不能为空");
        if (!ALGORITHM.equals(encrypted.algorithm())) {
            throw new IllegalArgumentException("不支持的凭据加密算法");
        }
        try (var masterKey = keyProvider.resolve(encrypted.masterKeyVersion())) {
            return masterKey.use(keyChars -> decrypt(encrypted, keyChars));
        }
    }

    private EncryptedSecret encrypt(char[] plaintext, char[] masterKeyChars, String masterKeyVersion) {
        byte[] value = utf8Bytes(plaintext);
        byte[] masterKey = keyBytes(masterKeyChars);
        byte[] dataKey = new byte[KEY_BYTES];
        byte[] dataKeyNonce = nonce();
        byte[] ciphertextNonce = nonce();
        random.nextBytes(dataKey);
        try {
            return new EncryptedSecret(
                    ALGORITHM,
                    masterKeyVersion,
                    dataKeyNonce,
                    encrypt(dataKey, masterKey, dataKeyNonce),
                    ciphertextNonce,
                    encrypt(value, dataKey, ciphertextNonce));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("凭据加密失败", exception);
        } finally {
            Arrays.fill(value, (byte) 0);
            Arrays.fill(masterKey, (byte) 0);
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    private SecretValue decrypt(EncryptedSecret encrypted, char[] masterKeyChars) {
        byte[] masterKey = keyBytes(masterKeyChars);
        byte[] dataKey = null;
        byte[] value = null;
        try {
            dataKey = decrypt(encrypted.encryptedDataKey(), masterKey, encrypted.dataKeyNonce());
            value = decrypt(encrypted.ciphertext(), dataKey, encrypted.ciphertextNonce());
            var chars = utf8Chars(value);
            try {
                return SecretValue.copyOf(chars);
            } finally {
                Arrays.fill(chars, '\0');
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("密文凭据无法解密", exception);
        } finally {
            Arrays.fill(masterKey, (byte) 0);
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
            if (value != null) {
                Arrays.fill(value, (byte) 0);
            }
        }
    }

    private byte[] nonce() {
        var value = new byte[NONCE_BYTES];
        random.nextBytes(value);
        return value;
    }

    private static byte[] encrypt(byte[] value, byte[] key, byte[] nonce)
            throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        return cipher.doFinal(value);
    }

    private static byte[] decrypt(byte[] ciphertext, byte[] key, byte[] nonce)
            throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        return cipher.doFinal(ciphertext);
    }

    private static byte[] utf8Bytes(char[] value) {
        var encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        var bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    private static char[] utf8Chars(byte[] value) {
        var decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(value));
        var chars = new char[decoded.remaining()];
        decoded.get(chars);
        return chars;
    }

    private static byte[] keyBytes(char[] keyChars) {
        var encoded = utf8Bytes(keyChars);
        try {
            var bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != KEY_BYTES) {
                Arrays.fill(bytes, (byte) 0);
                throw new IllegalArgumentException("AES-256 主密钥必须是 Base64 编码的 32 字节值");
            }
            return bytes;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("AES-256 主密钥必须是 Base64 编码的 32 字节值", exception);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static void requireKeyVersion(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("密钥版本不能为空");
        }
    }

    public record EncryptedSecret(
            String algorithm,
            String masterKeyVersion,
            byte[] dataKeyNonce,
            byte[] encryptedDataKey,
            byte[] ciphertextNonce,
            byte[] ciphertext) {

        public EncryptedSecret {
            Objects.requireNonNull(algorithm, "加密算法不能为空");
            requireKeyVersion(masterKeyVersion);
            dataKeyNonce = copyNonce(dataKeyNonce);
            encryptedDataKey = copyValue(encryptedDataKey, "封装数据密钥不能为空");
            ciphertextNonce = copyNonce(ciphertextNonce);
            ciphertext = copyValue(ciphertext, "密文不能为空");
        }

        @Override
        public byte[] dataKeyNonce() {
            return Arrays.copyOf(dataKeyNonce, dataKeyNonce.length);
        }

        @Override
        public byte[] encryptedDataKey() {
            return Arrays.copyOf(encryptedDataKey, encryptedDataKey.length);
        }

        @Override
        public byte[] ciphertextNonce() {
            return Arrays.copyOf(ciphertextNonce, ciphertextNonce.length);
        }

        @Override
        public byte[] ciphertext() {
            return Arrays.copyOf(ciphertext, ciphertext.length);
        }

        private static byte[] copyNonce(byte[] value) {
            var copy = copyValue(value, "随机数不能为空");
            if (copy.length != NONCE_BYTES) {
                throw new IllegalArgumentException("随机数格式不正确");
            }
            return copy;
        }

        private static byte[] copyValue(byte[] value, String message) {
            value = Objects.requireNonNull(value, message);
            if (value.length == 0) {
                throw new IllegalArgumentException(message);
            }
            return Arrays.copyOf(value, value.length);
        }
    }
}
