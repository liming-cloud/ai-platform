package com.aiplatform.foundation.modelruntime.application.provider.port.out;

import java.util.function.Supplier;

/**
 * 将连接根与其密文凭据作为单一事务提交，避免留下无法安全调用的连接。
 */
@FunctionalInterface
public interface ProviderConnectionCredentialTransaction {

    <T> T execute(Supplier<T> action);
}
