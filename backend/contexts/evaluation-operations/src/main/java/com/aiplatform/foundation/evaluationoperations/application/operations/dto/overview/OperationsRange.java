package com.aiplatform.foundation.evaluationoperations.application.operations.dto.overview;

import com.aiplatform.foundation.evaluationoperations.domain.operations.model.ReportingPeriod;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/** 运营总览允许选择的固定 UTC 自然日范围。 */
public enum OperationsRange {
    DAY_1(1),
    DAY_7(7),
    DAY_30(30);

    private final int days;

    OperationsRange(int days) {
        this.days = days;
    }

    /** 返回包含当前 UTC 自然日的固定窗口，结束时间为本次数据生成时间。 */
    public ReportingPeriod periodAt(Instant generatedAt) {
        generatedAt = Objects.requireNonNull(generatedAt, "数据生成时间不能为空");
        var from = generatedAt.atOffset(ZoneOffset.UTC)
                .toLocalDate()
                .minusDays(days - 1L)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        var to = generatedAt.equals(from) ? generatedAt.plusNanos(1L) : generatedAt;
        return new ReportingPeriod(from, to);
    }
}
