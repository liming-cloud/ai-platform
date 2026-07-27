package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 供应商连接生命周期状态。
 */
public enum ProviderConnectionStatus implements ValueObject {
    DRAFT,
    VERIFIED,
    DISABLED
}
