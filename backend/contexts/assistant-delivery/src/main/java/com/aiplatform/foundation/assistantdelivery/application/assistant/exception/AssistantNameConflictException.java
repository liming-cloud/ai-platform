package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 租户内助手名称重复。
 */
public final class AssistantNameConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantNameConflictException() {
        super(AssistantDeliveryErrorCode.NAME);
    }
}
