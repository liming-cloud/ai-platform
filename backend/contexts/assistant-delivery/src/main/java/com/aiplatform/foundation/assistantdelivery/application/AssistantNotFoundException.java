package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 当前租户内不存在指定助手。
 */
public final class AssistantNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantNotFoundException() {
        super("助手不存在");
    }
}
