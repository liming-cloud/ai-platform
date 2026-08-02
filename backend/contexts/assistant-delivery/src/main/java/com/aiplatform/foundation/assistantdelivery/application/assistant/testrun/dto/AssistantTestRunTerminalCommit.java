package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.dto;

import com.aiplatform.foundation.assistantdelivery.application.answer.dto.ModelInvocationTrace;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model.AssistantTestRun;
import java.util.Objects;

/**
 * 同一事务提交的草稿测试终态与内部模型调用轨迹。
 */
public record AssistantTestRunTerminalCommit(
        AssistantTestRun run,
        long expectedVersion,
        ModelInvocationTrace modelInvocationTrace) {

    public AssistantTestRunTerminalCommit {
        run = Objects.requireNonNull(run, "助手测试终态不能为空");
        if (run.status() == com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model
                .AssistantTestRunStatus.RUNNING || expectedVersion < 0) {
            throw new IllegalArgumentException("助手测试终态提交不正确");
        }
    }
}
