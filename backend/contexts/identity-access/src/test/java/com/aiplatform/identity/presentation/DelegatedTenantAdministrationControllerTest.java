package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.DelegatedTenantAdministrationApplicationService;
import com.aiplatform.identity.application.command.ExecuteDelegatedTenantActionCommand;
import com.aiplatform.identity.application.result.DelegatedTenantActionResult;
import com.aiplatform.identity.presentation.controller.DelegatedTenantAdministrationController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DelegatedTenantAdministrationControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void usesServerOperatorContextAndPathTargetTenant() throws Exception {
        TraceContext.ensureTraceId("TRACE-DELEGATION-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        DelegatedTenantAdministrationApplicationService service =
                mock(DelegatedTenantAdministrationApplicationService.class);
        when(service.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DelegatedTenantActionResult("8101", "SUCCEEDED", "AUDIT-001"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new DelegatedTenantAdministrationController(service)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/iam/tenants/9001/delegated-admin-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "REPLACE_ROLE_PERMISSIONS",
                                  "targetRoleId": "3002",
                                  "permissionCodes": ["knowledge-base:knowledge-base:read"],
                                  "reason": "应急修复错误授权",
                                  "idempotencyKey": "request-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-DELEGATION-001"))
                .andExpect(jsonPath("$.body.delegationActionId").value("8101"))
                .andExpect(jsonPath("$.body.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.body.auditEventId").value("AUDIT-001"));

        ArgumentCaptor<ExecuteDelegatedTenantActionCommand> captor =
                ArgumentCaptor.forClass(ExecuteDelegatedTenantActionCommand.class);
        verify(service).execute(captor.capture());
        assertThat(captor.getValue().operatorTenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().targetTenantId()).isEqualTo(TenantId.of("9001"));
        assertThat(captor.getValue().operatorId()).isEqualTo("1001");
        assertThat(captor.getValue().reason()).isEqualTo("应急修复错误授权");
    }

    @Test
    void rejectsRequestWithoutDelegationReasonBeforeApplicationService() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        DelegatedTenantAdministrationApplicationService service =
                mock(DelegatedTenantAdministrationApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new DelegatedTenantAdministrationController(service)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/iam/tenants/9001/delegated-admin-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "REPLACE_ROLE_PERMISSIONS",
                                  "targetRoleId": "3002",
                                  "permissionCodes": [],
                                  "reason": "",
                                  "idempotencyKey": "request-002"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.header.code").value("AIP-COMMON-VALIDATION-2001"));
    }
}
