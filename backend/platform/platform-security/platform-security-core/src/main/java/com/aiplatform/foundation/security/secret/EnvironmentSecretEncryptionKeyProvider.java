package com.aiplatform.foundation.security.secret;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 仅供本地研发从版本化环境变量读取主密钥，生产环境必须替换为 KMS 或 Vault 实现。
 */
public final class EnvironmentSecretEncryptionKeyProvider implements SecretEncryptionKeyProvider {

    private static final String VARIABLE_PREFIX = "FOUNDATION_SECRET_ENCRYPTION_KEY_";
    private static final Pattern VERSION = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private final String activeKeyVersion;
    private final Function<String, String> environment;

    public EnvironmentSecretEncryptionKeyProvider(String activeKeyVersion) {
        this(activeKeyVersion, System::getenv);
    }

    EnvironmentSecretEncryptionKeyProvider(
            String activeKeyVersion, Function<String, String> environment) {
        this.activeKeyVersion = requireVersion(activeKeyVersion);
        this.environment = Objects.requireNonNull(environment, "环境变量读取器不能为空");
    }

    @Override
    public ActiveKey activeKey() {
        return new ActiveKey(activeKeyVersion, resolve(activeKeyVersion));
    }

    @Override
    public SecretValue resolve(String keyVersion) {
        var version = requireVersion(keyVersion);
        var value = environment.apply(VARIABLE_PREFIX + version);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("版本化主密钥环境变量不存在或为空");
        }
        return SecretValue.copyOf(value.toCharArray());
    }

    private static String requireVersion(String value) {
        if (value == null || !VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("主密钥版本格式不正确");
        }
        return value;
    }
}
