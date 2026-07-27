package com.aiplatform.foundation.trustedquery.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 在线查询配额与运行租约配置。
 */
@ConfigurationProperties("foundation.query.quota")
public record TrustedQueryProperties(
        int dailyLimit,
        int concurrentLimit,
        Duration runningLease) {

    public TrustedQueryProperties {
        dailyLimit = dailyLimit == 0 ? 100 : dailyLimit;
        concurrentLimit = concurrentLimit == 0 ? 4 : concurrentLimit;
        runningLease = runningLease == null ? Duration.ofMinutes(2) : runningLease;
        if (dailyLimit < 1 || concurrentLimit < 1
                || runningLease.isZero() || runningLease.isNegative()) {
            throw new IllegalArgumentException("查询配额和运行租约配置不正确");
        }
    }
}
