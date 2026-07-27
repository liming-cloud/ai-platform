package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.common.ModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 不泄漏领域或持久化类型的查询事件公共视图。
 */
public record QueryRunEventView(
        long sequence,
        String type,
        String status,
        String answer,
        String refusalReason,
        List<QueryCitationView> citations,
        ModelUsage usage,
        Instant occurredAt,
        boolean terminal) {

    public QueryRunEventView {
        if (sequence < 1) {
            throw new IllegalArgumentException("查询事件序号必须大于零");
        }
        Objects.requireNonNull(type, "查询事件类型不能为空");
        Objects.requireNonNull(status, "查询状态不能为空");
        citations = List.copyOf(
                Objects.requireNonNull(citations, "查询引用不能为空"));
        Objects.requireNonNull(usage, "模型用量不能为空");
        Objects.requireNonNull(occurredAt, "事件时间不能为空");
    }
}
