package com.aiplatform.foundation.assistantdelivery.application.answer.service;

import com.aiplatform.foundation.assistantdelivery.application.answer.dto.AssistantTrustedAnswerExecutionObservation;
import java.util.Objects;

/**
 * 保留可信回答失败前已收集事实的内部异常。
 */
public final class AssistantTrustedAnswerExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AssistantTrustedAnswerExecutionObservation observation;

    public AssistantTrustedAnswerExecutionException(
            AssistantTrustedAnswerExecutionObservation observation,
            RuntimeException cause) {
        super(Objects.requireNonNull(cause, "可信回答失败原因不能为空"));
        this.observation = Objects.requireNonNull(observation, "可信回答执行事实不能为空");
    }

    /**
     * 返回失败前已确认的最小执行事实。
     */
    public AssistantTrustedAnswerExecutionObservation observation() {
        return observation;
    }
}
