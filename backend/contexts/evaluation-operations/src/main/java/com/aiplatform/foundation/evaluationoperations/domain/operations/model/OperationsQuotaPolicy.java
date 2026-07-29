package com.aiplatform.foundation.evaluationoperations.domain.operations.model;

import com.aiplatform.foundation.shared.kernel.domain.DomainPolicy;

/**
 * 运营视角使用的查询配额策略快照。
 */
public record OperationsQuotaPolicy(
        int dailyQueryLimit,
        int concurrentQueryLimit) implements DomainPolicy {

    public OperationsQuotaPolicy {
        if (dailyQueryLimit < 1 || concurrentQueryLimit < 1) {
            throw new IllegalArgumentException("查询配额上限必须为正数");
        }
    }
}
