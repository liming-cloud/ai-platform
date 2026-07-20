package com.aiplatform.shared.web;

import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.web.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void successShouldUseCurrentTraceId() {
        TraceContext.ensureTraceId("trace-test");

        ApiResponse<String> response = ApiResponse.success("ok");

        Assertions.assertEquals("SUCCESS", response.header().code());
        Assertions.assertEquals("trace-test", response.header().traceId());
        Assertions.assertEquals("ok", response.body());
    }
}
