package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialInspector;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationCredentialEntity;
import com.aiplatform.foundation.security.secret.AesGcmSecretCipher;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Function;

/**
 * 将模型 API Key 作为信封密文保存，并仅以短生命周期秘密回调形式读取。
 */
public final class PostgresEncryptedModelCredentialStore
        implements ModelConfigurationCredentialWriter, ModelConfigurationCredentialInspector {

    private final ModelConfigurationCredentialMapper mapper;
    private final SecretEncryptionKeyProvider keyProvider;
    private final AesGcmSecretCipher cipher;
    private final Clock clock;

    public PostgresEncryptedModelCredentialStore(
            ModelConfigurationCredentialMapper mapper,
            SecretEncryptionKeyProvider keyProvider) {
        this(mapper, keyProvider, new AesGcmSecretCipher(), Clock.systemUTC());
    }

    PostgresEncryptedModelCredentialStore(
            ModelConfigurationCredentialMapper mapper,
            SecretEncryptionKeyProvider keyProvider,
            AesGcmSecretCipher cipher,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "模型配置凭据映射器不能为空");
        this.keyProvider = Objects.requireNonNull(keyProvider, "主密钥提供者不能为空");
        this.cipher = Objects.requireNonNull(cipher, "凭据加密器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public void rotate(ModelConfigurationId configurationId, SecretValue apiKey) {
        Objects.requireNonNull(configurationId, "模型配置标识不能为空");
        Objects.requireNonNull(apiKey, "API Key不能为空");
        var encrypted = cipher.encrypt(apiKey, keyProvider);
        mapper.upsert(
                configurationId.value(),
                encrypted.algorithm(),
                encrypted.masterKeyVersion(),
                encrypted.dataKeyNonce(),
                encrypted.encryptedDataKey(),
                encrypted.ciphertextNonce(),
                encrypted.ciphertext(),
                fingerprint(apiKey),
                clock.instant());
    }

    /**
     * 调用方必须先完成模型配置归属、授权、配额和预算检查。
     */
    public <T> T useCredential(
            ModelConfigurationId configurationId,
            Function<SecretValue, T> action) {
        Objects.requireNonNull(configurationId, "模型配置标识不能为空");
        Objects.requireNonNull(action, "凭据使用动作不能为空");
        var row = mapper.selectById(configurationId.value());
        if (row == null) {
            throw new IllegalStateException("模型配置未设置凭据");
        }
        var encrypted = new AesGcmSecretCipher.EncryptedSecret(
                row.getAlgorithm(),
                row.getMasterKeyVersion(),
                row.getDataKeyNonce(),
                row.getEncryptedDataKey(),
                row.getCiphertextNonce(),
                row.getCiphertext());
        try (var secret = cipher.decrypt(encrypted, keyProvider)) {
            return action.apply(secret);
        }
    }

    @Override
    public boolean isConfigured(ModelConfigurationId configurationId) {
        return mapper.existsByConfigurationId(
                Objects.requireNonNull(configurationId, "模型配置标识不能为空").value());
    }

    private static String fingerprint(SecretValue value) {
        return value.use(chars -> {
            var encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
            var bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
            } finally {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
        });
    }
}
