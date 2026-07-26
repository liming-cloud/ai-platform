package com.aiplatform.foundation.assistantdelivery.domain;

/**
 * 助手版本状态不允许当前操作。
 */
public final class AssistantVersionStateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantVersionStateException(String message) {
        super(message);
    }
}
