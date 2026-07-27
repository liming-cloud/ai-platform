package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 助手或助手版本已被并发修改。
 */
public final class AssistantConcurrentModificationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantConcurrentModificationException() {
        super(AssistantDeliveryErrorCode.CONCURRENT);
    }
}
