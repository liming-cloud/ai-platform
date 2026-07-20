package com.aiplatform.identity.infrastructure.notification;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 邀请通知 outbox 的 envelope 加密边界。
 *
 * <p>密文使用租户、邀请 ID、算法、格式版本和 key ID 作为 AAD，因而不能跨租户或跨邀请
 * 调换。历史 key 只允许解密，所有新写入均使用 active key。</p>
 */
@Component
public class InvitationNotificationPayloadCipher {
    static final String SCHEMA = "aip-invitation-outbox-v1";
    static final String ALGORITHM = "AES-256-GCM";
    private static final String JCA_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_URL_BYTES = 8_192;
    private static final int MAX_ENVELOPE_CHARS = 24_000;
    private static final int MAX_KEY_MATERIAL_CHARS = 512;
    private static final int MAX_PREVIOUS_KEYS_CHARS = 16_384;
    private static final int MAX_PREVIOUS_KEYS = 32;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final JsonMapper jsonMapper;
    private final String activeKeyId;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom secureRandom;

    @Autowired
    public InvitationNotificationPayloadCipher(
            JsonMapper jsonMapper,
            @Value("${aip.identity.invitation-outbox.kms-key-id}") String activeKeyId,
            @Value("${aip.identity.invitation-outbox.encryption-key}") String activeKey,
            @Value("${aip.identity.invitation-outbox.previous-keys:}") String previousKeys
    ) {
        this(jsonMapper, activeKeyId, activeKey, previousKeys, new SecureRandom());
    }

    InvitationNotificationPayloadCipher(
            JsonMapper jsonMapper,
            String activeKeyId,
            String activeKey,
            String previousKeys,
            SecureRandom secureRandom
    ) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON 转换器不能为空");
        this.activeKeyId = requireKeyId(activeKeyId);
        LinkedHashMap<String, SecretKeySpec> configuredKeys = new LinkedHashMap<>();
        configuredKeys.put(this.activeKeyId, encryptionKey(activeKey, "邀请通知 active 加密密钥"));
        parsePreviousKeys(previousKeys, configuredKeys);
        this.keys = Map.copyOf(configuredKeys);
        this.secureRandom = Objects.requireNonNull(secureRandom, "安全随机数生成器不能为空");
    }

    public String encrypt(String tenantId, String invitationId, String activationUrl) {
        String normalizedTenantId = requireContext(tenantId, "租户 ID");
        String normalizedInvitationId = requireContext(invitationId, "邀请 ID");
        String plaintext = requireText(activationUrl, "邀请激活地址");
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        if (plainBytes.length > MAX_URL_BYTES) {
            throw new IllegalArgumentException("邀请激活地址长度超限");
        }
        byte[] nonce = new byte[IV_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] encrypted = crypt(
                Cipher.ENCRYPT_MODE,
                plainBytes,
                nonce,
                keys.get(activeKeyId),
                aad(normalizedTenantId, normalizedInvitationId, activeKeyId)
        );
        InvitationNotificationPayloadEnvelope envelope = new InvitationNotificationPayloadEnvelope(
                SCHEMA,
                ALGORITHM,
                activeKeyId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
                Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
        );
        try {
            return jsonMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException("邀请通知密文序列化失败", exception);
        }
    }

    public String decrypt(String tenantId, String invitationId, String envelopeJson) {
        String normalizedTenantId = requireContext(tenantId, "租户 ID");
        String normalizedInvitationId = requireContext(invitationId, "邀请 ID");
        String serialized = requireText(envelopeJson, "邀请通知密文");
        if (serialized.length() > MAX_ENVELOPE_CHARS) {
            throw malformedCiphertext();
        }
        try {
            InvitationNotificationPayloadEnvelope envelope = jsonMapper.readValue(
                    serialized,
                    InvitationNotificationPayloadEnvelope.class
            );
            if (envelope == null
                    || !SCHEMA.equals(envelope.schema())
                    || !ALGORITHM.equals(envelope.algorithm())) {
                throw malformedCiphertext();
            }
            String keyId = requireKeyId(envelope.keyId());
            SecretKeySpec key = keys.get(keyId);
            if (key == null) {
                throw malformedCiphertext();
            }
            byte[] nonce = decode(envelope.nonce());
            byte[] encrypted = decode(envelope.ciphertext());
            if (nonce.length != IV_BYTES
                    || encrypted.length <= TAG_BITS / Byte.SIZE
                    || encrypted.length > MAX_URL_BYTES + TAG_BITS / Byte.SIZE) {
                throw malformedCiphertext();
            }
            byte[] plaintext = crypt(
                    Cipher.DECRYPT_MODE,
                    encrypted,
                    nonce,
                    key,
                    aad(normalizedTenantId, normalizedInvitationId, keyId)
            );
            if (plaintext.length > MAX_URL_BYTES) {
                throw malformedCiphertext();
            }
            return requireText(new String(plaintext, StandardCharsets.UTF_8), "邀请激活地址");
        } catch (IllegalArgumentException exception) {
            throw malformedCiphertext();
        } catch (Exception exception) {
            throw malformedCiphertext();
        }
    }

    private static byte[] crypt(int mode, byte[] input, byte[] nonce, SecretKeySpec key, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(JCA_ALGORITHM);
            cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            if (mode == Cipher.DECRYPT_MODE) {
                throw malformedCiphertext();
            }
            throw new IllegalStateException("邀请通知加密失败", exception);
        }
    }

    private static byte[] aad(String tenantId, String invitationId, String keyId) {
        return (SCHEMA + '\0' + ALGORITHM + '\0' + keyId + '\0' + tenantId + '\0' + invitationId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decode(String value) {
        String encoded = requireText(value, "邀请通知密文字段");
        if (encoded.length() > MAX_ENVELOPE_CHARS) {
            throw malformedCiphertext();
        }
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw malformedCiphertext();
        }
    }

    private void parsePreviousKeys(String previousKeys, Map<String, SecretKeySpec> target) {
        if (previousKeys == null || previousKeys.isBlank()) {
            return;
        }
        if (!previousKeys.equals(previousKeys.trim()) || previousKeys.length() > MAX_PREVIOUS_KEYS_CHARS) {
            throw new IllegalArgumentException("邀请通知历史加密密钥配置非法");
        }
        String[] entries = previousKeys.split(",", -1);
        if (entries.length > MAX_PREVIOUS_KEYS) {
            throw new IllegalArgumentException("邀请通知历史加密密钥数量超限");
        }
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("邀请通知历史加密密钥配置非法");
            }
            String keyId = requireKeyId(entry.substring(0, separator));
            if (target.putIfAbsent(
                    keyId,
                    encryptionKey(entry.substring(separator + 1), "邀请通知历史加密密钥")
            ) != null) {
                throw new IllegalArgumentException("邀请通知加密 key ID 重复");
            }
        }
    }

    private static SecretKeySpec encryptionKey(String material, String fieldName) {
        String value = requireText(material, fieldName);
        String lower = value.toLowerCase(Locale.ROOT);
        if (!value.equals(material)
                || value.length() < 32
                || value.length() > MAX_KEY_MATERIAL_CHARS
                || lower.contains("replace")
                || lower.contains("changeme")
                || lower.contains("example")) {
            throw new IllegalArgumentException(fieldName + "必须为至少 32 个字符的独立随机密钥");
        }
        if (value.startsWith("b64:")) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(value.substring(4));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(fieldName + "必须包含有效的 Base64 密钥材料");
            }
            if (decoded.length != 32 || distinctByteCount(decoded) <= 1) {
                throw new IllegalArgumentException(fieldName + "必须解码为 32 字节独立随机密钥");
            }
            return new SecretKeySpec(decoded, "AES");
        }
        try {
            return new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)),
                    "AES"
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private static int distinctByteCount(byte[] bytes) {
        boolean[] seen = new boolean[256];
        int count = 0;
        for (byte value : bytes) {
            int index = Byte.toUnsignedInt(value);
            if (!seen[index]) {
                seen[index] = true;
                count++;
            }
        }
        return count;
    }

    private static String requireKeyId(String value) {
        String keyId = requireText(value, "邀请通知 key ID");
        if (!keyId.equals(value) || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("邀请通知 key ID 非法");
        }
        return keyId;
    }

    private static String requireContext(String value, String fieldName) {
        String normalized = requireText(value, fieldName);
        if (!normalized.equals(value) || normalized.length() > 128 || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(fieldName + "非法");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static IllegalArgumentException malformedCiphertext() {
        return new IllegalArgumentException("邀请通知密文格式非法");
    }
}
