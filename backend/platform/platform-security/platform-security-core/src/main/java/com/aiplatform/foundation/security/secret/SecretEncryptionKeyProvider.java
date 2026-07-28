package com.aiplatform.foundation.security.secret;

import java.util.Objects;

/**
 * 提供当前主密钥及历史版本主密钥，主密钥不得进入业务数据库。
 */
public interface SecretEncryptionKeyProvider {

    ActiveKey activeKey();

    SecretValue resolve(String keyVersion);

    /**
     * 当前用于封装数据密钥的主密钥；调用方必须在使用后关闭。
     */
    record ActiveKey(String version, SecretValue key) implements AutoCloseable {

        public ActiveKey {
            if (version == null || version.isBlank() || !version.equals(version.trim())) {
                throw new IllegalArgumentException("密钥版本不能为空");
            }
            Objects.requireNonNull(key, "主密钥不能为空");
        }

        @Override
        public void close() {
            key.close();
        }
    }
}
