package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 回答引用要求。
 */
public enum CitationPolicy implements ValueObject {
    REQUIRED,
    OPTIONAL
}
