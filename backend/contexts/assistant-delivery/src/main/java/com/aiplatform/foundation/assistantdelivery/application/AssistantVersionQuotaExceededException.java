package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 单个助手的版本数量超过限制。
 */
public final class AssistantVersionQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantVersionQuotaExceededException() {
        super("助手版本数量已达到上限");
    }
}
