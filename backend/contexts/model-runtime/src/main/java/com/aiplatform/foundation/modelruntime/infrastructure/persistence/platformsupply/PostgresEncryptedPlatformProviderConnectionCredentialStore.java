package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.secret.AesGcmSecretCipher;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 平台连接专用的密文凭据写入器。
 *
 * <p>它不实现通用 `SecretResolver`，防止租户运行时在未经平台供应链适配的情况下解析平台凭据。</p>
 */
public final class PostgresEncryptedPlatformProviderConnectionCredentialStore
        implements PlatformProviderConnectionCredentialWriter, PlatformProviderCredentialResolver {

    private static final String INFERENCE_STORE = "platform-provider-connection";
    private static final String DISCOVERY_ACCESS_KEY_STORE = "platform-provider-discovery-access-key";
    private static final String DISCOVERY_SECRET_KEY_STORE = "platform-provider-discovery-secret-key";

    private final JdbcTemplate jdbc;
    private final SecretEncryptionKeyProvider keyProvider;
    private final AesGcmSecretCipher cipher;
    private final Clock clock;

    public PostgresEncryptedPlatformProviderConnectionCredentialStore(
            JdbcTemplate jdbc,
            SecretEncryptionKeyProvider keyProvider) {
        this(jdbc, keyProvider, new AesGcmSecretCipher(), Clock.systemUTC());
    }

    PostgresEncryptedPlatformProviderConnectionCredentialStore(
            JdbcTemplate jdbc,
            SecretEncryptionKeyProvider keyProvider,
            AesGcmSecretCipher cipher,
            Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "数据库访问器不能为空");
        this.keyProvider = Objects.requireNonNull(keyProvider, "主密钥提供者不能为空");
        this.cipher = Objects.requireNonNull(cipher, "凭据加密器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public void write(CredentialReference reference, SecretValue apiKey) {
        var slot = slot(reference);
        var encrypted = cipher.encrypt(Objects.requireNonNull(apiKey, "API Key不能为空"), keyProvider);
        jdbc.update("""
                INSERT INTO model_runtime.platform_provider_connection_credential (
                    platform_provider_connection_id, credential_purpose, algorithm, master_key_version, data_key_nonce,
                    encrypted_data_key, ciphertext_nonce, ciphertext, fingerprint, rotated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (platform_provider_connection_id, credential_purpose) DO UPDATE SET
                    algorithm = EXCLUDED.algorithm,
                    master_key_version = EXCLUDED.master_key_version,
                    data_key_nonce = EXCLUDED.data_key_nonce,
                    encrypted_data_key = EXCLUDED.encrypted_data_key,
                    ciphertext_nonce = EXCLUDED.ciphertext_nonce,
                    ciphertext = EXCLUDED.ciphertext,
                    fingerprint = EXCLUDED.fingerprint,
                    rotated_at = EXCLUDED.rotated_at
                """,
                slot.connectionId(), slot.purpose(), encrypted.algorithm(), encrypted.masterKeyVersion(),
                encrypted.dataKeyNonce(), encrypted.encryptedDataKey(), encrypted.ciphertextNonce(),
                encrypted.ciphertext(), fingerprint(apiKey), Timestamp.from(clock.instant()));
    }

    @Override
    public SecretValue resolve(CredentialReference reference) {
        var slot = slot(reference);
        var encrypted = jdbc.query("""
                SELECT algorithm, master_key_version, data_key_nonce, encrypted_data_key,
                       ciphertext_nonce, ciphertext
                FROM model_runtime.platform_provider_connection_credential
                WHERE platform_provider_connection_id = ? AND credential_purpose = ?
                """, result -> result.next()
                        ? new AesGcmSecretCipher.EncryptedSecret(
                                result.getString("algorithm"),
                                result.getString("master_key_version"),
                                result.getBytes("data_key_nonce"),
                                result.getBytes("encrypted_data_key"),
                                result.getBytes("ciphertext_nonce"),
                                result.getBytes("ciphertext"))
                        : null,
                slot.connectionId(), slot.purpose());
        if (encrypted == null) {
            throw new IllegalStateException("平台供应商连接未设置指定用途的凭据");
        }
        return cipher.decrypt(encrypted, keyProvider);
    }

    private static CredentialSlot slot(CredentialReference reference) {
        var uri = URI.create(Objects.requireNonNull(reference, "平台私有凭据引用不能为空").value());
        if (!"secret".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("平台供应商连接凭据引用格式不正确");
        }
        var purpose = switch (uri.getHost().toLowerCase(java.util.Locale.ROOT)) {
            case INFERENCE_STORE -> "INFERENCE_API_KEY";
            case DISCOVERY_ACCESS_KEY_STORE -> "DISCOVERY_ACCESS_KEY_ID";
            case DISCOVERY_SECRET_KEY_STORE -> "DISCOVERY_SECRET_ACCESS_KEY";
            default -> throw new IllegalArgumentException("平台供应商连接凭据引用格式不正确");
        };
        return new CredentialSlot(Long.parseLong(uri.getPath().substring(1)), purpose);
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

    private record CredentialSlot(long connectionId, String purpose) {
    }
}
