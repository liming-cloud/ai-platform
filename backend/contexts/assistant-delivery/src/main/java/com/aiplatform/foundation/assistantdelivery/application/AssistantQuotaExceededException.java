package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 租户助手数量超过限制。
 */
public final class AssistantQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantQuotaExceededException() {
        super("租户助手数量已达到上限");
    }
}
