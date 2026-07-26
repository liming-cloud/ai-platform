package com.aiplatform.foundation.security.secret;

/**
 * 从受管密钥存储中按引用解析短生命周期秘密。
 */
@FunctionalInterface
public interface SecretResolver {

    SecretValue resolve(SecretReference reference);
}
