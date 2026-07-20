package com.aiplatform.identity.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.IamConfigurationQueryApplicationService;
import com.aiplatform.identity.application.result.IamUserConfigurationPage;
import com.aiplatform.identity.application.result.IamUserConfigurationResult;
import com.aiplatform.identity.presentation.controller.IamConfigurationController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IamUserPaginationControllerTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TraceContext.clear();
    }

    @Test
    void returnsTenantUserPageEnvelopeFromTrustedTenantContext() throws Exception {
        TenantContext.set(TENANT_ID);
        TraceContext.ensureTraceId("TRACE-IAM-USER-PAGE-001");
        IamConfigurationQueryApplicationService service = mock(IamConfigurationQueryApplicationService.class);
        when(service.listUsers(TENANT_ID, 2, 10)).thenReturn(new IamUserConfigurationPage(
                List.of(new IamUserConfigurationResult(
                        "1011", "chen.xiao", "陈晓", "chen.xiao@example.com", "ACTIVE", List.of("business-user")
                )),
                11,
                2,
                10
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationController(service)).build();

        mockMvc.perform(get("/api/v1/iam/users")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-USER-PAGE-001"))
                .andExpect(jsonPath("$.body.items[0].userId").value("1011"))
                .andExpect(jsonPath("$.body.page").value(2))
                .andExpect(jsonPath("$.body.pageSize").value(10))
                .andExpect(jsonPath("$.body.total").value(11))
                .andExpect(jsonPath("$.body.totalPages").value(2));

        verify(service).listUsers(TENANT_ID, 2, 10);
    }
}
