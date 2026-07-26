package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnection;
import java.util.Objects;

/**
 * 供应商连接创建或历史重放结果。
 */
public record ProviderConnectionCreationOutcome(
        ProviderConnection connection,
        boolean replayed) {

    public ProviderConnectionCreationOutcome {
        Objects.requireNonNull(connection, "供应商连接不能为空");
    }
}
