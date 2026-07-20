package com.aiplatform.summary.presentation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.summary.application.SummaryOpsApplicationService;
import com.aiplatform.summary.application.result.TenantWorkbenchResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SummaryOpsControllerTest {
    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        PrincipalContext.clear();
        TraceContext.clear();
    }

    @Test
    void tenantQueryParameterCannotOverrideAuthenticatedTenant() throws Exception {
        SummaryOpsApplicationService service = mock(SummaryOpsApplicationService.class);
        when(service.tenantWorkbench(eq(TenantId.of("2001")), eq("1002"), anyString()))
                .thenReturn(new TenantWorkbenchResult(
                        "2001",
                        Instant.parse("2026-07-10T08:00:00Z"),
                        "REALTIME",
                        false,
                        List.of(),
                        "trace-tenant",
                        List.of("summary-ops"),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()
                ));
        SummaryOpsController controller = new SummaryOpsController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1002"));
        TraceContext.ensureTraceId("trace-tenant");

        mockMvc.perform(get("/api/v1/summary/tenant-workbench").param("tenantId", "9999"))
                .andExpect(status().isOk());

        verify(service).tenantWorkbench(TenantId.of("2001"), "1002", "trace-tenant");
    }
}
