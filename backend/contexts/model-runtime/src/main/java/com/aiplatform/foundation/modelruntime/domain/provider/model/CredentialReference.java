package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/**
 * 领域内不透明的受管凭据引用，不承载凭据明文或具体密钥框架类型。
 */
public record CredentialReference(String value) implements ValueObject {

    public CredentialReference {
        Objects.requireNonNull(value, "凭据引用不能为空");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 512) {
            throw new IllegalArgumentException("凭据引用格式不正确");
        }
    }
}
