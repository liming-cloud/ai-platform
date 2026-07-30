package com.aiplatform.foundation.trustedquery.domain.query.feedback.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QueryFeedbackTest {

    private static final TenantId TENANT = new TenantId(100000000000000001L);
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T15:00:00Z");

    @Test
    void 应以不可变最小事实记录正向反馈() {
        var feedback = QueryFeedback.record(
                TENANT,
                new QueryFeedbackId(1L),
                new QueryRunId(2L),
                new QueryFeedbackRequesterId(3L),
                QueryFeedbackOutcome.POSITIVE,
                QueryFeedbackReason.HELPFUL,
                CREATED_AT);

        assertEquals(TENANT, feedback.tenantId());
        assertEquals(2L, feedback.queryRunId().value());
        assertEquals(3L, feedback.requestedBy().value());
        assertEquals(QueryFeedbackReason.HELPFUL, feedback.reason());
    }

    @Test
    void 不匹配的结果和受控原因必须被拒绝() {
        assertThrows(IllegalArgumentException.class, () -> QueryFeedback.record(
                TENANT,
                new QueryFeedbackId(1L),
                new QueryRunId(2L),
                new QueryFeedbackRequesterId(3L),
                QueryFeedbackOutcome.POSITIVE,
                QueryFeedbackReason.INCORRECT,
                CREATED_AT));

        assertDoesNotThrow(() -> QueryFeedback.record(
                TENANT,
                new QueryFeedbackId(1L),
                new QueryRunId(2L),
                new QueryFeedbackRequesterId(3L),
                QueryFeedbackOutcome.NEGATIVE,
                QueryFeedbackReason.INSUFFICIENT,
                CREATED_AT));
    }

    @Test
    void 标识必须为正数且反馈不持有任何正文() {
        assertThrows(IllegalArgumentException.class, () -> new QueryFeedbackId(0L));
        assertThrows(IllegalArgumentException.class, () -> new QueryFeedbackRequesterId(0L));
        assertEquals(0, Arrays.stream(QueryFeedback.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class)
                .count());
    }
}
