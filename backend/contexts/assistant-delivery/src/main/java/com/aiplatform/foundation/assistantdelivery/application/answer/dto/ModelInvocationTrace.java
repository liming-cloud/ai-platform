package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

/**
 * 路由聊天调用的最小内部事实，不携带请求、回答、供应商、端点或错误文本。
 */
public record ModelInvocationTrace(
        int attempts,
        long selectedConfigurationId,
        long selectedConfigurationRevision) {

    public ModelInvocationTrace {
        if (attempts < 1 || selectedConfigurationId < 1 || selectedConfigurationRevision < 1) {
            throw new IllegalArgumentException("模型调用轨迹不完整");
        }
    }
}
