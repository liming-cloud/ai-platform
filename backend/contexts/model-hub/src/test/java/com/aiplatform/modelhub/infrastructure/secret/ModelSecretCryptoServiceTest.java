package com.aiplatform.modelhub.infrastructure.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class ModelSecretCryptoServiceTest {
    private static final String VALID_KMS_KEY_ID = "kms-primary-2026";
    private static final String VALID_ENCRYPTION_KEY = "v".repeat(32);
    private static final String NEXT_ENCRYPTION_KEY = "n".repeat(32);

    @Test
    void encryptsSecretAndOnlyExposesDigestAndMaskedValue() {
        ModelSecretCryptoService service = new ModelSecretCryptoService(VALID_KMS_KEY_ID, VALID_ENCRYPTION_KEY);

        ModelSecretMaterial material = service.encrypt("sk-live-plain-secret");

        assertThat(material.kmsKeyId()).isEqualTo(VALID_KMS_KEY_ID);
        assertThat(material.cipherAlgorithm()).isEqualTo("AES-256-GCM");
        assertThat(material.cipherVersion()).isEqualTo(2);
        assertThat(material.ciphertext()).startsWith("aip-secret:v2:AES-256-GCM:");
        assertThat(material.ciphertext()).doesNotContain("sk-live-plain-secret");
        assertThat(material.digest()).hasSize(64);
        assertThat(material.maskedValue()).isEqualTo("sk-***cret");
        assertThat(service.decrypt(material.ciphertext())).isEqualTo("sk-live-plain-secret");
    }

    @Test
    void decryptsLegacyV1WithPreviousKeyAndValidatesPersistedDigest() {
        String legacyCiphertext = legacyEncrypt("sk-legacy-secret", VALID_ENCRYPTION_KEY);
        ModelSecretCryptoService service = new ModelSecretCryptoService(
                "kms-next-2026",
                NEXT_ENCRYPTION_KEY,
                VALID_KMS_KEY_ID + "=" + VALID_ENCRYPTION_KEY
        );

        String decrypted = service.decrypt(
                legacyCiphertext,
                sha256Hex("sk-legacy-secret"),
                VALID_KMS_KEY_ID,
                "AES-256-GCM",
                1
        );

        assertThat(decrypted).isEqualTo("sk-legacy-secret");
    }

    @Test
    void failsClosedForUnknownKeyOrPersistedMetadataMismatch() {
        ModelSecretCryptoService service = new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY
        );
        ModelSecretMaterial material = service.encrypt("sk-live-plain-secret");

        assertThatThrownBy(() -> service.decrypt(
                legacyEncrypt("sk-live-plain-secret", VALID_ENCRYPTION_KEY),
                sha256Hex("sk-live-plain-secret"),
                "kms-unknown",
                "AES-256-GCM",
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("模型供应商凭证材料密钥不可用");
        assertThatThrownBy(() -> service.decrypt(
                material.ciphertext(),
                material.digest(),
                material.kmsKeyId(),
                "AES-CBC",
                material.cipherVersion()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型供应商凭证材料加密元数据不合法");
        assertThatThrownBy(() -> service.decrypt(
                material.ciphertext(),
                sha256Hex("different-secret"),
                material.kmsKeyId(),
                material.cipherAlgorithm(),
                material.cipherVersion()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("模型供应商凭证材料完整性校验失败");
    }

    @Test
    void failsClosedForMalformedOrOversizedV2EnvelopeFields() {
        ModelSecretCryptoService service = new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY
        );

        assertThatThrownBy(() -> service.decrypt("aip-secret:v2:AES-256-GCM:not-base64:iv.cipher"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型供应商凭证材料密文格式不合法");
        assertThatThrownBy(() -> service.decrypt(
                "aip-secret:v2:AES-256-GCM:" + "a".repeat(1024) + ":iv.cipher"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型供应商凭证材料密文格式不合法");
    }

    @Test
    void authenticatesV2KeyIdentityEvenWhenTwoIdsResolveToTheSameAesKey() {
        String previousKeyId = "kms-alias-2025";
        ModelSecretCryptoService service = new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                previousKeyId + "=" + VALID_ENCRYPTION_KEY
        );
        ModelSecretMaterial material = service.encrypt("sk-live-plain-secret");
        String activeEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(VALID_KMS_KEY_ID.getBytes(StandardCharsets.UTF_8));
        String previousEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(previousKeyId.getBytes(StandardCharsets.UTF_8));
        String tampered = material.ciphertext().replace(activeEncoded + ":", previousEncoded + ":");

        assertThatThrownBy(() -> service.decrypt(
                tampered,
                material.digest(),
                previousKeyId,
                material.cipherAlgorithm(),
                material.cipherVersion()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("模型供应商凭证材料解密失败");
    }

    @Test
    void springUsesPublicConstructorForDependencyInjection() {
        boolean hasAutowiredConstructor = Arrays.stream(ModelSecretCryptoService.class.getConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Autowired.class));

        assertThat(hasAutowiredConstructor).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"local-dev-kms", "replace-model-kms-key-id", "changeme-kms", " kms-primary-2026"})
    void rejectsWeakOrPlaceholderKmsKeyIdentifiersAtConstruction(String kmsKeyId) {
        assertThatThrownBy(() -> new ModelSecretCryptoService(kmsKeyId, VALID_ENCRYPTION_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KMS 密钥 ID");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "local-dev-secret-key-change-before-prod",
            "replace-model-encryption-key",
            "password-with-known-weak-prefix",
            "short-encryption-key",
            " 0123456789abcdef0123456789abcdef"
    })
    void rejectsWeakOrPlaceholderEncryptionKeysAtConstruction(String encryptionKey) {
        assertThatThrownBy(() -> new ModelSecretCryptoService(VALID_KMS_KEY_ID, encryptionKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型密钥加密密钥");
    }

    @Test
    void rejectsMissingModelSecretConfigurationAtConstruction() {
        assertThatThrownBy(() -> new ModelSecretCryptoService(null, VALID_ENCRYPTION_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KMS 密钥 ID不能为空");
        assertThatThrownBy(() -> new ModelSecretCryptoService(VALID_KMS_KEY_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型密钥加密密钥不能为空");
    }

    @Test
    void rejectsDuplicateActiveOrDuplicatePreviousKeyIds() {
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                VALID_KMS_KEY_ID + "=" + NEXT_ENCRYPTION_KEY
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("历史模型密钥 ID 不能与 active key ID 重复");
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                "kms-old=" + VALID_ENCRYPTION_KEY + ",kms-old=" + NEXT_ENCRYPTION_KEY
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("历史模型密钥 ID 不能重复");
    }

    @Test
    void rejectsUnboundedOrMalformedPreviousKeyConfiguration() {
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                "x".repeat(20_000)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("历史模型密钥配置长度超限");
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                "kms-old=" + "x".repeat(300)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("历史模型加密密钥");
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                "kms-old =" + NEXT_ENCRYPTION_KEY
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("历史模型密钥 ID");
        StringBuilder tooManyKeys = new StringBuilder();
        for (int index = 0; index < 33; index++) {
            if (index > 0) {
                tooManyKeys.append(',');
            }
            tooManyKeys.append("kms-old-").append(index).append('=').append(NEXT_ENCRYPTION_KEY);
        }
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                VALID_ENCRYPTION_KEY,
                tooManyKeys.toString()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("历史模型密钥条目数量超限");
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                "k".repeat(129),
                VALID_ENCRYPTION_KEY
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KMS 密钥 ID格式不合法");
    }

    @Test
    void supportsBoundedBase64EncodedAes256KeyMaterial() {
        byte[] keyBytes = new byte[32];
        for (int index = 0; index < keyBytes.length; index++) {
            keyBytes[index] = (byte) index;
        }
        String encodedKey = "b64:" + Base64.getEncoder().encodeToString(keyBytes);
        ModelSecretCryptoService service = new ModelSecretCryptoService(VALID_KMS_KEY_ID, encodedKey);

        ModelSecretMaterial material = service.encrypt("sk-live-plain-secret");

        assertThat(service.decrypt(material.ciphertext())).isEqualTo("sk-live-plain-secret");
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                "b64:" + Base64.getEncoder().encodeToString(new byte[65])
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须解码为 32 字节 AES-256 key");
        assertThatThrownBy(() -> new ModelSecretCryptoService(
                VALID_KMS_KEY_ID,
                "b64:" + Base64.getEncoder().encodeToString(new byte[32])
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能使用明显弱 key material");
    }

    private static String legacyEncrypt(String plaintext, String encryptionKey) {
        try {
            byte[] iv = new byte[12];
            Arrays.fill(iv, (byte) 7);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                            .digest(encryptionKey.getBytes(StandardCharsets.UTF_8)), "AES"),
                    new GCMParameterSpec(128, iv)
            );
            return "aes-gcm:v1:" + Base64.getEncoder().encodeToString(iv) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
