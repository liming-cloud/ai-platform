package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.PermissionRequestApplicationService;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequest;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestPage;
import com.aiplatform.identity.domain.permissionrequest.PermissionRequestStatus;
import com.aiplatform.identity.presentation.controller.PermissionRequestController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PermissionRequestControllerTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void decisionUsesTrustedTenantPrincipalAndTraceContexts() throws Exception {
        TraceContext.ensureTraceId("TRACE-PERMISSION-CONTROLLER-001");
        TenantContext.set(TENANT_ID);
        PrincipalContext.set(new AuthenticatedPrincipal(TENANT_ID, "user", "1002"));
        PermissionRequestApplicationService service = mock(PermissionRequestApplicationService.class);
        when(service.decide(any(), any(), any(), any(), any(), any())).thenReturn(decided());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PermissionRequestController(service)).build();

        mockMvc.perform(post("/api/v1/permission-requests/request-1/actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"comment\":\"批准\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("TRACE-PERMISSION-CONTROLLER-001"))
                .andExpect(jsonPath("$.body.requestId").value("request-1"))
                .andExpect(jsonPath("$.body.status").value("APPROVED"));

        ArgumentCaptor<String> traceId = ArgumentCaptor.forClass(String.class);
        verify(service).decide(
                eq(TENANT_ID),
                eq("1002"),
                eq("request-1"),
                eq("APPROVE"),
                eq("批准"),
                traceId.capture()
        );
        assertThat(traceId.getValue())
                .isEqualTo("TRACE-PERMISSION-CONTROLLER-001");
    }

    @Test
    void listReturnsStablePageEnvelopeInsteadOfAnUnboundedArray() throws Exception {
        TraceContext.ensureTraceId("TRACE-PERMISSION-LIST-001");
        TenantContext.set(TENANT_ID);
        PermissionRequestApplicationService service = mock(PermissionRequestApplicationService.class);
        when(service.list(TENANT_ID, "PENDING", 2, 20))
                .thenReturn(new PermissionRequestPage(List.of(decided()), 21, 2, 20));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PermissionRequestController(service)).build();

        mockMvc.perform(get("/api/v1/permission-requests")
                        .queryParam("status", "PENDING")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.items[0].requestId").value("request-1"))
                .andExpect(jsonPath("$.body.page").value(2))
                .andExpect(jsonPath("$.body.pageSize").value(20))
                .andExpect(jsonPath("$.body.total").value(21))
                .andExpect(jsonPath("$.body.totalPages").value(2));

        verify(service).list(TENANT_ID, "PENDING", 2, 20);
    }

    private static PermissionRequest decided() {
        return new PermissionRequest(
                TENANT_ID,
                "request-1",
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                "需要完成合同审核",
                PermissionRequestStatus.APPROVED,
                "1002",
                "批准",
                NOW.minusSeconds(60),
                NOW
        );
    }
}
