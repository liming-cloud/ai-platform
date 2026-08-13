package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;

/** 平台范围不可变模型发现事实的写入端口。 */
@FunctionalInterface
public interface PlatformProviderModelDiscoveryStore {

    /** 原子保存已冻结的平台模型发现事实。 */
    void record(PlatformProviderModelDiscovery discovery);
}
