package com.aiplatform.foundation.assistantdelivery.domain.assistant.model;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.DomainException;

/**
 * 助手版本状态不允许当前操作。
 */
public final class AssistantVersionStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AssistantVersionStateException(String message) {
        super(AssistantDeliveryErrorCode.VERSION_STATE);
    }
}
