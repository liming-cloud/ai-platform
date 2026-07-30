package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun;

import java.time.Instant;
import java.util.Objects;

/**
 * 草稿测试的独立日限额或并发运行限额已耗尽。
 */
public final class AssistantTestQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AssistantTestQuotaLimitType limitType;
    private final int limit;
    private final Instant retryAt;

    public AssistantTestQuotaExceededException(
            AssistantTestQuotaLimitType limitType,
            int limit,
            Instant retryAt) {
        super("助手草稿测试配额已耗尽");
        this.limitType = Objects.requireNonNull(limitType, "配额限制类型不能为空");
        if (limit < 1) {
            throw new IllegalArgumentException("配额限制必须大于零");
        }
        this.limit = limit;
        this.retryAt = Objects.requireNonNull(retryAt, "重试时间不能为空");
    }

    public AssistantTestQuotaLimitType limitType() {
        return limitType;
    }

    public int limit() {
        return limit;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
