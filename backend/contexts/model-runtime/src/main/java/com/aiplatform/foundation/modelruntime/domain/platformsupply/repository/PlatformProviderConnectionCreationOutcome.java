package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import java.util.Objects;

/** 平台供应商连接的创建或幂等重放结果。 */
public record PlatformProviderConnectionCreationOutcome(
        PlatformProviderConnection connection,
        boolean replayed) {

    public PlatformProviderConnectionCreationOutcome {
        Objects.requireNonNull(connection, "平台供应商连接不能为空");
    }
}
