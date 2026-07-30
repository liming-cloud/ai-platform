package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.policy.AssistantTestQuotaPolicy;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 草稿测试被接受时冻结的独立日配额和并发运行租约。
 */
public record AssistantTestQuotaSnapshot(
        Instant periodStart,
        Instant periodEnd,
        int dailyLimit,
        int dailyOrdinal,
        int concurrentLimit,
        Instant acceptedLeaseUntil) implements ValueObject {

    public AssistantTestQuotaSnapshot {
        Objects.requireNonNull(periodStart, "配额周期开始时间不能为空");
        Objects.requireNonNull(periodEnd, "配额周期结束时间不能为空");
        Objects.requireNonNull(acceptedLeaseUntil, "运行租约截止时间不能为空");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("配额周期结束时间必须晚于开始时间");
        }
        if (dailyLimit < 1 || dailyOrdinal < 1 || dailyOrdinal > dailyLimit || concurrentLimit < 1) {
            throw new IllegalArgumentException("助手测试配额占用事实不正确");
        }
    }

    /**
     * 根据部署固定策略生成一次已接受草稿测试的 UTC 配额快照。
     */
    public static AssistantTestQuotaSnapshot forAcceptedTest(
            Instant acceptedAt,
            AssistantTestQuotaPolicy policy,
            int dailyOrdinal) {
        Objects.requireNonNull(acceptedAt, "助手测试接受时间不能为空");
        Objects.requireNonNull(policy, "助手测试配额策略不能为空");
        var periodStart = acceptedAt.atOffset(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        return new AssistantTestQuotaSnapshot(
                periodStart,
                periodStart.plus(Duration.ofDays(1)),
                policy.dailyTestLimit(),
                dailyOrdinal,
                policy.concurrentTestLimit(),
                acceptedAt.plus(policy.runningLease()));
    }
}
