package com.aiplatform.foundation.modelruntime.domain.provider.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 当前平台能够显式装配的供应商协议类型。
 */
public enum ProviderKind implements ValueObject {
    DETERMINISTIC,
    OPENAI_COMPATIBLE,
    OLLAMA
}
