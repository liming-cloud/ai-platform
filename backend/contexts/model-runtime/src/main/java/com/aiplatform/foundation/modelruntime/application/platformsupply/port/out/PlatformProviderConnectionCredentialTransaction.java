package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import java.util.function.Supplier;

/** 平台连接元数据和密文凭据必须共享的事务边界。 */
@FunctionalInterface
public interface PlatformProviderConnectionCredentialTransaction {

    <T> T execute(Supplier<T> action);
}
