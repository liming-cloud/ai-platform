package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRunStatus;
import java.util.List;
import java.util.Objects;

/**
 * 草稿助手测试的当前调用结果；问题和回答不会作为运行历史持久化。
 */
public record AssistantTestRunResult(
        String assistantTestRunId,
        AssistantTestRunStatus status,
        String answer,
        List<AssistantTestCitationResult> citations) {

    public AssistantTestRunResult {
        Objects.requireNonNull(assistantTestRunId, "助手测试运行标识不能为空");
        status = Objects.requireNonNull(status, "助手测试状态不能为空");
        citations = List.copyOf(Objects.requireNonNull(citations, "助手测试引用不能为空"));
        if (status == AssistantTestRunStatus.ANSWERED && (answer == null || answer.isBlank() || citations.isEmpty())) {
            throw new IllegalArgumentException("已回答测试必须包含当前回答和引用");
        }
        if (status != AssistantTestRunStatus.ANSWERED && (answer != null || !citations.isEmpty())) {
            throw new IllegalArgumentException("非回答测试不能携带当前回答或引用");
        }
    }
}
