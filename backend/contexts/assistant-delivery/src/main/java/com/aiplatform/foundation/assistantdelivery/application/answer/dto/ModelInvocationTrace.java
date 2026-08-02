package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

import java.util.List;
import java.util.Objects;

/**
 * 路由聊天调用的最小内部事实，不携带请求、回答、供应商、端点或错误文本。
 */
public record ModelInvocationTrace(List<Attempt> attempts) {

    private static final int MAX_ATTEMPTS = 3;

    public ModelInvocationTrace {
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty() || attempts.size() > MAX_ATTEMPTS || attempts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("模型调用轨迹不完整");
        }
        for (var index = 0; index < attempts.size(); index++) {
            if (attempts.get(index).sequence() != index + 1) {
                throw new IllegalArgumentException("模型调用轨迹必须按冻结顺序连续编号");
            }
            if (index < attempts.size() - 1 && attempts.get(index).outcome() != Outcome.TRANSIENT_FAILURE) {
                throw new IllegalArgumentException("仅最后一次模型调用可以成功");
            }
        }
    }

    /**
     * 一次冻结候选调用的最小事实。
     */
    public record Attempt(int sequence, long configurationId, long configurationRevision, Outcome outcome) {

        public Attempt {
            if (sequence < 1 || configurationId < 1 || configurationRevision < 1) {
                throw new IllegalArgumentException("模型调用候选不完整");
            }
            outcome = Objects.requireNonNull(outcome, "模型调用结果不能为空");
        }
    }

    /**
     * 不暴露供应商错误细节的受控调用结果。
     */
    public enum Outcome {
        SUCCEEDED,
        TRANSIENT_FAILURE
    }
}
