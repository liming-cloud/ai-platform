package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户内不存在指定助手版本。
 */
public final class AssistantVersionNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantVersionNotFoundException() {
        super(AssistantDeliveryErrorCode.VERSION_NOT_FOUND);
    }
}
