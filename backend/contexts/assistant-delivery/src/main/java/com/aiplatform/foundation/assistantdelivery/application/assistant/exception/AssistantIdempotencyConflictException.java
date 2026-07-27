package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 同一幂等键被用于不同助手请求。
 */
public final class AssistantIdempotencyConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantIdempotencyConflictException() {
        super(AssistantDeliveryErrorCode.IDEMPOTENCY);
    }
}
