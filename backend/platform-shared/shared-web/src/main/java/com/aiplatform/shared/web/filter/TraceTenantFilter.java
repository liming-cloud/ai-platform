package com.aiplatform.shared.web.filter;

import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceTenantFilter extends OncePerRequestFilter {
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = TraceContext.startTraceId();
            response.setHeader(TRACE_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            TenantContext.clear();
        }
    }
}
