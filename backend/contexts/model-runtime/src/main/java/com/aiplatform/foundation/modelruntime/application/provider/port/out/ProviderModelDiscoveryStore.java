package com.aiplatform.foundation.modelruntime.application.provider.port.out;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscovery;

/** 不可变模型发现事实的写入端口。 */
@FunctionalInterface
public interface ProviderModelDiscoveryStore {

    /**
     * 原子保存一次已由领域规则冻结的发现事实。
     *
     * @throws IllegalStateException 存储不能完整提交该事实时抛出。
     */
    void record(ProviderModelDiscovery discovery);
}
