package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 单个助手的版本数量超过限制。
 */
public final class AssistantVersionQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantVersionQuotaExceededException() {
        super(AssistantDeliveryErrorCode.VERSION_QUOTA);
    }
}
