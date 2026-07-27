package com.aiplatform.foundation.trustedquery.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.foundation.platform.web.error.FoundationExceptionHandler;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaLimitType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TrustedQueryExceptionMappingTest {

    @Test
    void 查询配额异常只返回登记过的稳定详情() {
        var handler = new FoundationExceptionHandler(() -> 540000000000000001L);
        var response = handler.foundationException(
                new QueryQuotaExceededException(
                        QueryQuotaLimitType.DAILY,
                        100,
                        Instant.parse("2026-07-28T00:00:00Z")));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("TRUSTED_QUERY.QUOTA.0001");
        assertThat(response.getBody().details()).isEqualTo(Map.of(
                "limitType", "DAILY",
                "limit", 100,
                "retryAt", "2026-07-28T00:00:00Z"));
    }
}
