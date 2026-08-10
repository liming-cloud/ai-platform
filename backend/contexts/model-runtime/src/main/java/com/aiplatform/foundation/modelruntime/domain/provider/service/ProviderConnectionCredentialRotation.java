package com.aiplatform.foundation.modelruntime.domain.provider.service;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnection;
import java.util.Objects;

/**
 * 将秘密轮换转译为连接修订失效，避免应用层补写状态机规则。
 */
public final class ProviderConnectionCredentialRotation {

    public ProviderConnectionCredentialRotationResult rotate(
            ProviderConnection connection,
            CredentialReference replacement) {
        return new ProviderConnectionCredentialRotationResult(
                Objects.requireNonNull(connection, "供应商连接不能为空")
                        .rotateCredential(Objects.requireNonNull(replacement, "私有凭据引用不能为空")));
    }
}
