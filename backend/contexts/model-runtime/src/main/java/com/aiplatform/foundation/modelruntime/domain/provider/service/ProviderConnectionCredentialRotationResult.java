package com.aiplatform.foundation.modelruntime.domain.provider.service;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import java.util.Objects;

/**
 * 凭据轮换后已失效探测事实的不可变领域操作结果。
 */
public record ProviderConnectionCredentialRotationResult(ProviderConnection connection) {

    public ProviderConnectionCredentialRotationResult {
        Objects.requireNonNull(connection, "供应商连接不能为空");
    }
}
