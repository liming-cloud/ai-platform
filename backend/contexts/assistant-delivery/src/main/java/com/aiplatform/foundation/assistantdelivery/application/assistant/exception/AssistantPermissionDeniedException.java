package com.aiplatform.foundation.assistantdelivery.application.assistant.exception;

import com.aiplatform.foundation.assistantdelivery.domain.common.error.AssistantDeliveryErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前主体缺少助手操作权限。
 */
public final class AssistantPermissionDeniedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AssistantPermissionDeniedException() {
        super(AssistantDeliveryErrorCode.ACCESS_DENIED);
    }
}
