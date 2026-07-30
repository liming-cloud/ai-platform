package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy;

import com.aiplatform.foundation.shared.kernel.domain.DomainPolicy;
import java.time.Duration;
import java.util.Objects;

/**
 * 定义部署侧固定的草稿测试日限额、并发限额和运行租约。
 */
public record AssistantTestQuotaPolicy(
        int dailyTestLimit,
        int concurrentTestLimit,
        Duration runningLease) implements DomainPolicy {

    public AssistantTestQuotaPolicy {
        if (dailyTestLimit < 1 || concurrentTestLimit < 1) {
            throw new IllegalArgumentException("助手测试配额必须大于零");
        }
        Objects.requireNonNull(runningLease, "助手测试运行租约不能为空");
        if (runningLease.isZero() || runningLease.isNegative()) {
            throw new IllegalArgumentException("助手测试运行租约必须大于零");
        }
    }
}
