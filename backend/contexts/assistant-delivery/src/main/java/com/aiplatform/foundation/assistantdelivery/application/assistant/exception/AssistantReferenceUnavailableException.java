package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 助手版本引用的模型或知识索引不是可冻结的已发布事实。
 */
public final class AssistantReferenceUnavailableException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantReferenceUnavailableException() {
        super(AssistantDeliveryErrorCode.REFERENCE_UNAVAILABLE);
    }
}
