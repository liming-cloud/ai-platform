package com.aiplatform.foundation.modelruntime.domain.deployment.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
/**
 * 模型供应可声明的类型；类型存在不代表已具备该类型的运行能力。
 */
public enum ModelType implements ValueObject {
    CHAT,
    EMBEDDING,
    RERANK
}
