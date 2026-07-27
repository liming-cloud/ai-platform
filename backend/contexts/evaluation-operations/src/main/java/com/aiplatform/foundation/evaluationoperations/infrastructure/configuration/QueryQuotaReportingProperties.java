package com.aiplatform.foundation.evaluationoperations.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运营报表读取的查询配额配置，与可信查询执行侧使用同一配置命名空间。
 */
@ConfigurationProperties("foundation.query.quota")
public record QueryQuotaReportingProperties(
        int dailyLimit,
        int concurrentLimit) {

    public QueryQuotaReportingProperties {
        dailyLimit = dailyLimit == 0 ? 100 : dailyLimit;
        concurrentLimit = concurrentLimit == 0 ? 4 : concurrentLimit;
        if (dailyLimit < 1 || concurrentLimit < 1) {
            throw new IllegalArgumentException("查询配额上限必须大于零");
        }
    }
}
