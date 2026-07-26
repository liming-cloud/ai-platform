package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 助手版本引用的模型或知识索引不是可冻结的已发布事实。
 */
public final class AssistantReferenceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantReferenceUnavailableException(String message) {
        super(message);
    }
}
