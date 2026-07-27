package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 证据不足时的回答策略。
 */
public enum RefusalPolicy implements ValueObject {
    INSUFFICIENT_EVIDENCE,
    MODEL_DISCRETION
}
