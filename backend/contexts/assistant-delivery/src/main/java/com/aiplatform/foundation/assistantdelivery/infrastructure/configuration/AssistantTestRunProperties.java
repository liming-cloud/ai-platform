package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 草稿助手测试的独立配额与运行租约配置。
 */
@ConfigurationProperties("foundation.assistant-delivery.test-run")
public record AssistantTestRunProperties(
        int dailyLimit,
        int concurrentLimit,
        Duration runningLease) {

    public AssistantTestRunProperties {
        dailyLimit = dailyLimit == 0 ? 20 : dailyLimit;
        concurrentLimit = concurrentLimit == 0 ? 2 : concurrentLimit;
        runningLease = runningLease == null ? Duration.ofMinutes(2) : runningLease;
        if (dailyLimit < 1 || concurrentLimit < 1 || runningLease.isZero() || runningLease.isNegative()) {
            throw new IllegalArgumentException("草稿助手测试配额和运行租约配置不正确");
        }
    }
}
