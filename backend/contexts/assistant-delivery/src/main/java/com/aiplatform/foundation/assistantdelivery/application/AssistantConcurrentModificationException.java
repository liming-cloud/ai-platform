package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 助手或助手版本已被并发修改。
 */
public final class AssistantConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantConcurrentModificationException() {
        super("助手已被并发修改");
    }
}
