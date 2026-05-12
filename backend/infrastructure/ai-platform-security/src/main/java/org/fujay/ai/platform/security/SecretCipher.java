package org.fujay.ai.platform.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

    private static final String PREFIX = "v1";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    public SecretCipher(@Value("${ai.platform.secret.master-key}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("平台主密钥不能为空");
        }
        this.keySpec = new SecretKeySpec(sha256(masterKey), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("密钥明文不能为空");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":" + base64(iv) + ":" + base64(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("密钥加密失败", ex);
        }
    }

    public String decrypt(String encrypted) {
        String[] parts = encrypted == null ? new String[0] : encrypted.split(":");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("密钥密文格式不正确");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(cipher.doFinal(ciphertext))).toString();
        } catch (Exception ex) {
            throw new IllegalStateException("密钥解密失败", ex);
        }
    }

    public String last4(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        String trimmed = plaintext.trim();
        return trimmed.substring(Math.max(0, trimmed.length() - 4));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("主密钥派生失败", ex);
        }
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
