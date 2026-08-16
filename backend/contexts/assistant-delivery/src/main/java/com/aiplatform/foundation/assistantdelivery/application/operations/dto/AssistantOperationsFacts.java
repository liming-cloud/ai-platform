package com.aiplatform.foundation.assistantdelivery.application.operations.dto;

import java.util.Map;

/** 当前租户的助手、发布版本与测试运行脱敏计数。 */
public record AssistantOperationsFacts(
        long assistants,
        Map<String, Long> versionsByStatus,
        Map<String, Long> testRunsByStatus) {

    public AssistantOperationsFacts {
        if (assistants < 0L) {
            throw new IllegalArgumentException("助手数量不能为负数");
        }
        versionsByStatus = Map.copyOf(versionsByStatus);
        testRunsByStatus = Map.copyOf(testRunsByStatus);
    }
}
