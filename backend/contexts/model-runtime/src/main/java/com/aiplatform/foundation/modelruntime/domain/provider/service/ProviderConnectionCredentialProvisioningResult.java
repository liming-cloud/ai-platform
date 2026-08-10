package com.aiplatform.foundation.modelruntime.domain.provider.service;

import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import java.util.Objects;

/**
 * 新建连接与其私有凭据关联的不可变领域操作结果。
 */
public record ProviderConnectionCredentialProvisioningResult(ProviderConnection connection) {

    public ProviderConnectionCredentialProvisioningResult {
        Objects.requireNonNull(connection, "供应商连接不能为空");
    }
}
