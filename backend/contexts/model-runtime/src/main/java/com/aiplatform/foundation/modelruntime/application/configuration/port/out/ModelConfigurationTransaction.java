package com.aiplatform.foundation.modelruntime.application.configuration.port.out;

import java.util.function.Supplier;

/**
 * 将配置元数据与加密凭据写入纳入同一事务，避免产生无凭据的可用配置。
 */
@FunctionalInterface
public interface ModelConfigurationTransaction {

    <T> T execute(Supplier<T> action);
}
