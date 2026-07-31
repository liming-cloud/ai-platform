package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto;

/**
 * 草稿测试当前响应中的可展示引用，不进入草稿测试持久化历史。
 */
public record AssistantTestCitationResult(
        int sequence,
        String dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String quote,
        double score) {
}
