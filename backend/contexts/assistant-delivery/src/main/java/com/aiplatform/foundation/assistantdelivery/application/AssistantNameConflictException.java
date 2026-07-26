package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 租户内助手名称重复。
 */
public final class AssistantNameConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantNameConflictException() {
        super("租户内助手名称已存在");
    }
}
