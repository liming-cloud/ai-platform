package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 助手版本生命周期状态。
 */
public enum AssistantVersionStatus implements ValueObject {
    DRAFT,
    PUBLISHED
}
