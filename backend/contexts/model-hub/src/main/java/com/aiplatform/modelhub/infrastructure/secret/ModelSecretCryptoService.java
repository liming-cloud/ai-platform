package com.aiplatform.modelhub.infrastructure.secret;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
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

/**
 * 模型供应商凭证材料 envelope 加密服务。
 *
 * <p>新密文把 key ID、算法和格式版本绑定为 AES-GCM AAD；历史 v1 密文只在持久化元数据
 * 明确指定旧 key 时兼容读取。任何未知 key、元数据不一致或无界输入都失败关闭。</p>
 */
@Component
public class ModelSecretCryptoService {
    static final String CIPHER_ALGORITHM = "AES-256-GCM";
    static final int CIPHER_VERSION = 2;
    static final int LEGACY_CIPHER_VERSION = 1;

    private static final String JCA_ALGORITHM = "AES/GCM/NoPadding";
    private static final String LEGACY_PREFIX = "aes-gcm:v1:";
    private static final String V2_PREFIX = "aip-secret:v2:" + CIPHER_ALGORITHM + ":";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MAX_KEY_ID_CHARS = 128;
    private static final int MAX_KEY_MATERIAL_CHARS = 512;
    private static final int MAX_LEGACY_KEY_BYTES = 256;
    private static final int MAX_PREVIOUS_KEYS_CHARS = 16_384;
    private static final int MAX_PREVIOUS_KEYS = 32;
    private static final int MAX_PLAINTEXT_BYTES = 16_384;
    private static final int MAX_CIPHERTEXT_CHARS = 32_768;
    private static final int MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + (TAG_LENGTH_BITS / Byte.SIZE);
    private static final Pattern SECURE_KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1," + MAX_KEY_ID_CHARS + "}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final String activeKeyId;
    private final Map<String, SecretKeySpec> encryptionKeys;
    private final SecureRandom secureRandom;

    @Autowired
    public ModelSecretCryptoService(
            @Value("${aip.model-hub.secret.kms-key-id}") String activeKeyId,
            @Value("${aip.model-hub.secret.encryption-key}") String activeEncryptionKey,
            @Value("${aip.model-hub.secret.previous-keys:}") String previousKeys
    ) {
        this(activeKeyId, activeEncryptionKey, previousKeys, new SecureRandom());
    }

    public ModelSecretCryptoService(String activeKeyId, String activeEncryptionKey) {
        this(activeKeyId, activeEncryptionKey, "", new SecureRandom());
    }

    ModelSecretCryptoService(
            String activeKeyId,
            String activeEncryptionKey,
            String previousKeys,
            SecureRandom secureRandom
    ) {
        this.activeKeyId = requireSecureIdentifier(activeKeyId, "KMS 密钥 ID");
        LinkedHashMap<String, SecretKeySpec> keys = new LinkedHashMap<>();
        keys.put(this.activeKeyId, encryptionKey(activeEncryptionKey, "模型密钥加密密钥"));
        parsePreviousKeys(previousKeys, this.activeKeyId, keys);
        this.encryptionKeys = Map.copyOf(keys);
        this.secureRandom = Objects.requireNonNull(secureRandom, "安全随机数生成器不能为空");
    }

    public ModelSecretMaterial encrypt(String plainCredentialMaterial) {
        String plainText = requireText(plainCredentialMaterial, "模型供应商凭证材料");
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        if (plainBytes.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("模型供应商凭证材料长度超限");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        byte[] encrypted = encrypt(
                plainBytes,
                iv,
                encryptionKeys.get(activeKeyId),
                authenticatedMetadata(activeKeyId)
        );
        String encodedKeyId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(activeKeyId.getBytes(StandardCharsets.UTF_8));
        String ciphertext = V2_PREFIX + encodedKeyId + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        return new ModelSecretMaterial(
                ciphertext,
                HexFormat.of().formatHex(sha256(plainBytes)),
                mask(plainText),
                activeKeyId,
                CIPHER_ALGORITHM,
                CIPHER_VERSION
        );
    }

    /**
     * 仅供新 envelope 的局部调用使用。数据库读取必须调用带持久化元数据和摘要的重载。
     */
    String decrypt(String ciphertext) {
        ParsedModelSecretCiphertext parsed = parseV2(ciphertext);
        return decryptV2(parsed);
    }

    public String decrypt(
            String ciphertext,
            String expectedDigest,
            String persistedKeyId,
            String persistedAlgorithm,
            Integer persistedVersion
    ) {
        String keyId = requireSecureIdentifier(persistedKeyId, "持久化 KMS 密钥 ID");
        if (!CIPHER_ALGORITHM.equals(persistedAlgorithm)
                || (persistedVersion == null
                || (persistedVersion != LEGACY_CIPHER_VERSION && persistedVersion != CIPHER_VERSION))) {
            throw new IllegalArgumentException("模型供应商凭证材料加密元数据不合法");
        }
        String plaintext;
        if (persistedVersion == LEGACY_CIPHER_VERSION) {
            plaintext = decryptLegacy(ciphertext, keyId);
        } else {
            ParsedModelSecretCiphertext parsed = parseV2(ciphertext);
            if (!keyId.equals(parsed.keyId())) {
                throw new IllegalArgumentException("模型供应商凭证材料加密元数据不合法");
            }
            plaintext = decryptV2(parsed);
        }
        verifyDigest(plaintext, expectedDigest);
        return plaintext;
    }

    public String activeKeyId() {
        return activeKeyId;
    }

    public String targetAlgorithm() {
        return CIPHER_ALGORITHM;
    }

    public int targetVersion() {
        return CIPHER_VERSION;
    }

    private String decryptV2(ParsedModelSecretCiphertext parsed) {
        SecretKeySpec key = availableKey(parsed.keyId());
        byte[] decrypted = decrypt(parsed.encrypted(), parsed.iv(), key, authenticatedMetadata(parsed.keyId()));
        if (decrypted.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String ciphertext, String keyId) {
        String encryptedText = boundedCiphertext(ciphertext);
        if (!encryptedText.startsWith(LEGACY_PREFIX)) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        String[] parts = encryptedText.substring(LEGACY_PREFIX.length()).split("\\.", -1);
        if (parts.length != 2 || parts[0].length() > 32 || parts[1].length() > 24_000) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        byte[] iv = decodeStandardBase64(parts[0]);
        byte[] encrypted = decodeStandardBase64(parts[1]);
        validateCipherBytes(iv, encrypted);
        byte[] decrypted = decrypt(encrypted, iv, availableKey(keyId), null);
        if (decrypted.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private ParsedModelSecretCiphertext parseV2(String ciphertext) {
        String encryptedText = boundedCiphertext(ciphertext);
        if (!encryptedText.startsWith(V2_PREFIX)) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        String payload = encryptedText.substring(V2_PREFIX.length());
        int keySeparator = payload.indexOf(':');
        if (keySeparator < 1 || keySeparator > 172 || keySeparator == payload.length() - 1) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        String encodedKeyId = payload.substring(0, keySeparator);
        String encryptedPayload = payload.substring(keySeparator + 1);
        String[] parts = encryptedPayload.split("\\.", -1);
        if (parts.length != 2 || parts[0].length() > 32 || parts[1].length() > 24_000) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        byte[] keyIdBytes = decodeUrlBase64(encodedKeyId);
        if (keyIdBytes.length < 1 || keyIdBytes.length > MAX_KEY_ID_CHARS) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);
        try {
            keyId = requireSecureIdentifier(keyId, "密文 KMS 密钥 ID");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        byte[] iv = decodeUrlBase64(parts[0]);
        byte[] encrypted = decodeUrlBase64(parts[1]);
        validateCipherBytes(iv, encrypted);
        return new ParsedModelSecretCiphertext(keyId, iv, encrypted);
    }

    private static void validateCipherBytes(byte[] iv, byte[] encrypted) {
        if (iv.length != IV_LENGTH_BYTES || encrypted.length <= TAG_LENGTH_BITS / Byte.SIZE
                || encrypted.length > MAX_ENCRYPTED_BYTES) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
    }

    private static String boundedCiphertext(String ciphertext) {
        String value = requireText(ciphertext, "密钥密文");
        if (value.length() > MAX_CIPHERTEXT_CHARS) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
        return value;
    }

    private SecretKeySpec availableKey(String keyId) {
        SecretKeySpec key = encryptionKeys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("模型供应商凭证材料密钥不可用");
        }
        return key;
    }

    private static byte[] authenticatedMetadata(String keyId) {
        return ("aip-secret\u0000v2\u0000" + CIPHER_ALGORITHM + "\u0000" + keyId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] encrypt(byte[] plainBytes, byte[] iv, SecretKeySpec key, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(JCA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(plainBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("模型供应商凭证材料加密失败", exception);
        }
    }

    private static byte[] decrypt(byte[] encryptedBytes, byte[] iv, SecretKeySpec key, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(JCA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(encryptedBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("模型供应商凭证材料解密失败", exception);
        }
    }

    private static void verifyDigest(String plaintext, String expectedDigest) {
        if (expectedDigest == null || !SHA256_HEX.matcher(expectedDigest).matches()) {
            throw new IllegalStateException("模型供应商凭证材料完整性校验失败");
        }
        byte[] expected = HexFormat.of().parseHex(expectedDigest);
        byte[] actual = sha256(plaintext.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalStateException("模型供应商凭证材料完整性校验失败");
        }
    }

    private static byte[] decodeUrlBase64(String encoded) {
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
    }

    private static byte[] decodeStandardBase64(String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("模型供应商凭证材料密文格式不合法");
        }
    }

    private static SecretKeySpec encryptionKey(String value, String fieldName) {
        String material = requireText(value, fieldName);
        if (material.length() > MAX_KEY_MATERIAL_CHARS) {
            throw new IllegalArgumentException(fieldName + "长度超限");
        }
        if (!material.equals(material.trim())) {
            throw new IllegalArgumentException(fieldName + "不能包含首尾空白");
        }
        if (isKnownWeakValue(material)) {
            throw new IllegalArgumentException(fieldName + "不能使用占位或已知弱值");
        }
        if (material.startsWith("b64:")) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(material.substring(4));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(fieldName + "必须使用合法 Base64");
            }
            if (decoded.length != 32) {
                throw new IllegalArgumentException(fieldName + "必须解码为 32 字节 AES-256 key");
            }
            boolean hasVariation = false;
            for (int index = 1; index < decoded.length; index++) {
                if (decoded[index] != decoded[0]) {
                    hasVariation = true;
                    break;
                }
            }
            if (!hasVariation) {
                throw new IllegalArgumentException(fieldName + "不能使用明显弱 key material");
            }
            return new SecretKeySpec(decoded, "AES");
        }
        byte[] legacyBytes = material.getBytes(StandardCharsets.UTF_8);
        if (legacyBytes.length < 32) {
            throw new IllegalArgumentException(fieldName + "长度不能少于 32 个字节");
        }
        if (legacyBytes.length > MAX_LEGACY_KEY_BYTES) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + MAX_LEGACY_KEY_BYTES + " 个字节");
        }
        return new SecretKeySpec(sha256(legacyBytes), "AES");
    }

    private static void parsePreviousKeys(
            String rawPreviousKeys,
            String activeKeyId,
            Map<String, SecretKeySpec> keys
    ) {
        if (rawPreviousKeys == null || rawPreviousKeys.isEmpty()) {
            return;
        }
        if (rawPreviousKeys.length() > MAX_PREVIOUS_KEYS_CHARS) {
            throw new IllegalArgumentException("历史模型密钥配置长度超限");
        }
        String[] entries = rawPreviousKeys.split(",", -1);
        if (entries.length > MAX_PREVIOUS_KEYS) {
            throw new IllegalArgumentException("历史模型密钥条目数量超限");
        }
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            if (separator < 1 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("历史模型密钥配置格式不合法");
            }
            String keyId = requireSecureIdentifier(entry.substring(0, separator), "历史模型密钥 ID");
            if (activeKeyId.equals(keyId)) {
                throw new IllegalArgumentException("历史模型密钥 ID 不能与 active key ID 重复");
            }
            if (keys.containsKey(keyId)) {
                throw new IllegalArgumentException("历史模型密钥 ID 不能重复");
            }
            keys.put(keyId, encryptionKey(entry.substring(separator + 1), "历史模型加密密钥"));
        }
    }

    private static String mask(String value) {
        if (value.length() <= 7) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 4);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireSecureIdentifier(String value, String fieldName) {
        String identifier = requireText(value, fieldName);
        if (!identifier.equals(identifier.trim())) {
            throw new IllegalArgumentException(fieldName + "不能包含首尾空白");
        }
        if (!SECURE_KEY_ID.matcher(identifier).matches()) {
            throw new IllegalArgumentException(fieldName + "格式不合法");
        }
        if (isKnownWeakValue(identifier)) {
            throw new IllegalArgumentException(fieldName + "不能使用占位或已知弱值");
        }
        return identifier;
    }

    private static boolean isKnownWeakValue(String value) {
        String comparable = value.toLowerCase(Locale.ROOT);
        return comparable.startsWith("local-dev")
                || comparable.startsWith("replace-")
                || comparable.startsWith("change-")
                || comparable.startsWith("changeme")
                || comparable.startsWith("example")
                || comparable.startsWith("password");
    }
}
