package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 当前租户内不存在指定助手版本。
 */
public final class AssistantVersionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantVersionNotFoundException() {
        super("助手版本不存在");
    }
}
