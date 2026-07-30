package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun;

/**
 * 草稿测试运行在终态提交前已被其他事务修改。
 */
public final class AssistantTestRunConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantTestRunConcurrentModificationException() {
        super("助手草稿测试运行已被并发修改");
    }
}
