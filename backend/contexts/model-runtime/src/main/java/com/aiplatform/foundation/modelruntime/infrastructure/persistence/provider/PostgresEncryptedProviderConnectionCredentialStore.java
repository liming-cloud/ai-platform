package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider;

import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper.ProviderConnectionCredentialMapper;
import com.aiplatform.foundation.security.secret.AesGcmSecretCipher;
import com.aiplatform.foundation.security.secret.EnvironmentSecretResolver;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretReference;
import com.aiplatform.foundation.security.secret.SecretResolver;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 以连接标识保存密文凭据，并兼容既有环境变量密钥解析。
 */
public final class PostgresEncryptedProviderConnectionCredentialStore
        implements ProviderConnectionCredentialWriter, SecretResolver {

    private static final String STORE = "provider-connection";

    private final ProviderConnectionCredentialMapper mapper;
    private final SecretEncryptionKeyProvider keyProvider;
    private final AesGcmSecretCipher cipher;
    private final Clock clock;
    private final SecretResolver fallback;

    public PostgresEncryptedProviderConnectionCredentialStore(
            ProviderConnectionCredentialMapper mapper,
            SecretEncryptionKeyProvider keyProvider) {
        this(mapper, keyProvider, new AesGcmSecretCipher(), Clock.systemUTC(), new EnvironmentSecretResolver());
    }

    PostgresEncryptedProviderConnectionCredentialStore(
            ProviderConnectionCredentialMapper mapper,
            SecretEncryptionKeyProvider keyProvider,
            AesGcmSecretCipher cipher,
            Clock clock,
            SecretResolver fallback) {
        this.mapper = Objects.requireNonNull(mapper, "厂商连接凭据映射器不能为空");
        this.keyProvider = Objects.requireNonNull(keyProvider, "主密钥提供者不能为空");
        this.cipher = Objects.requireNonNull(cipher, "凭据加密器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.fallback = Objects.requireNonNull(fallback, "既有密钥解析器不能为空");
    }

    @Override
    public void write(TenantId tenantId, CredentialReference reference, SecretValue apiKey) {
        var encrypted = cipher.encrypt(Objects.requireNonNull(apiKey, "API Key不能为空"), keyProvider);
        mapper.upsert(
                connectionId(Objects.requireNonNull(reference, "私有凭据引用不能为空").value()),
                Objects.requireNonNull(tenantId, "租户标识不能为空").value(),
                encrypted.algorithm(),
                encrypted.masterKeyVersion(),
                encrypted.dataKeyNonce(),
                encrypted.encryptedDataKey(),
                encrypted.ciphertextNonce(),
                encrypted.ciphertext(),
                fingerprint(apiKey),
                clock.instant());
    }

    @Override
    public SecretValue resolve(SecretReference reference) {
        Objects.requireNonNull(reference, "密钥引用不能为空");
        if (!isProviderConnectionReference(reference.value())) {
            return fallback.resolve(reference);
        }
        var row = mapper.selectByConnectionId(connectionId(reference.value()));
        if (row == null) {
            throw new IllegalStateException("厂商连接未设置凭据");
        }
        return cipher.decrypt(new AesGcmSecretCipher.EncryptedSecret(
                row.getAlgorithm(),
                row.getMasterKeyVersion(),
                row.getDataKeyNonce(),
                row.getEncryptedDataKey(),
                row.getCiphertextNonce(),
                row.getCiphertext()), keyProvider);
    }

    private static boolean isProviderConnectionReference(String value) {
        return STORE.equalsIgnoreCase(URI.create(value).getHost());
    }

    private static long connectionId(String value) {
        var uri = URI.create(value);
        if (!STORE.equalsIgnoreCase(uri.getHost())
                || uri.getPath() == null
                || !uri.getPath().matches("/[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("厂商连接凭据引用格式不正确");
        }
        return Long.parseLong(uri.getPath().substring(1));
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
