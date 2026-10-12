package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;

/** 原子提交平台连接验证结果与其绑定的不可变模型发现事实。 */
@FunctionalInterface
public interface PlatformProviderModelDiscoveryStore {

    /**
     * 在同一事务内比较连接旧修订并保存新修订、发现根和候选；任何写入失败必须全部回滚。
     * 外部厂商调用必须先结束，不得放入该事务。
     *
     * @throws com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException
     *         当前连接已停用、轮换或被其他请求更新时抛出，迟到结果不得写入。
     * @throws IllegalArgumentException 发现事实没有绑定本次验证的新连接修订时抛出。
     */
    void record(PlatformProviderConnection connection, long expectedVersion,
            PlatformProviderModelDiscovery discovery);
}
