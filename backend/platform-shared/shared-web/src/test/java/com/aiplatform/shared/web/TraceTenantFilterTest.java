package com.aiplatform.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.web.filter.TraceTenantFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceTenantFilterTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
    }

    @Test
    void filterGeneratesServerTraceAndIgnoresClientTenantHeader() throws Exception {
        TraceTenantFilter filter = new TraceTenantFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/model-providers");
        request.addHeader(TraceTenantFilter.TRACE_HEADER, "TRACE-FROM-CLIENT");
        request.addHeader("X-Tenant-Id", "2001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[2];
        FilterChain chain = (servletRequest, servletResponse) -> {
            observed[0] = TraceContext.currentTraceId();
            try {
                TenantContext.current();
                observed[1] = "initialized";
            } catch (IllegalStateException exception) {
                observed[1] = "not-initialized";
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(observed[0]).isNotBlank();
        assertThat(observed[0]).isNotEqualTo("TRACE-FROM-CLIENT");
        assertThat(response.getHeader(TraceTenantFilter.TRACE_HEADER)).isEqualTo(observed[0]);
        assertThat(observed[1]).isEqualTo("not-initialized");
        assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
    }
}
