package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * M1 支持的模型部署类型。
 */
public enum ModelType implements ValueObject {
    CHAT,
    EMBEDDING
}
