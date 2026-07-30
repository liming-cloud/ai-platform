package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 助手草稿测试运行的生命周期状态。
 */
public enum AssistantTestRunStatus implements ValueObject {
    RUNNING,
    ANSWERED,
    REFUSED,
    FAILED
}
