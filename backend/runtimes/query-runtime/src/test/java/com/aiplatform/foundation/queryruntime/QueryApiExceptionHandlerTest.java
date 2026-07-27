package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.bootstrap.query.web.QueryApiExceptionHandler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaLimitType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class QueryApiExceptionHandlerTest {

    @Test
    void 超额错误只返回稳定限额事实() {
        var response = new QueryApiExceptionHandler(
                () -> 540000000000000001L).quotaExceeded(
                new QueryQuotaExceededException(
                        QueryQuotaLimitType.DAILY,
                        100,
                        Instant.parse("2026-07-28T00:00:00Z")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("QUERY_QUOTA_EXCEEDED", response.getBody().code());
        assertEquals("DAILY", response.getBody().details().get("limitType"));
        assertEquals(100, response.getBody().details().get("limit"));
        assertEquals(
                "2026-07-28T00:00:00Z",
                response.getBody().details().get("retryAt"));
    }
}
