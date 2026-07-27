package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 租户助手数量超过限制。
 */
public final class AssistantQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantQuotaExceededException() {
        super(AssistantDeliveryErrorCode.ASSISTANT_QUOTA);
    }
}
