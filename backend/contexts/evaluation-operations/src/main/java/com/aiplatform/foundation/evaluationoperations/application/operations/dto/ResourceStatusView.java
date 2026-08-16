package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

import java.util.Map;

/** 资源总数及其业务状态分布。 */
public record ResourceStatusView(long total, Map<String, Long> statuses) {

    public ResourceStatusView {
        statuses = Map.copyOf(statuses);
        if (total < 0L || statuses.values().stream().anyMatch(value -> value == null || value < 0L)) {
            throw new IllegalArgumentException("资源状态计数不能为负数");
        }
        if (statuses.values().stream().mapToLong(Long::longValue).sum() != total) {
            throw new IllegalArgumentException("资源总数必须等于状态计数之和");
        }
    }

    public static ResourceStatusView from(Map<String, Long> statuses) {
        var copied = Map.copyOf(statuses);
        return new ResourceStatusView(copied.values().stream().mapToLong(Long::longValue).sum(), copied);
    }
}
