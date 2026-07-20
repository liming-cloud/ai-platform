package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.IamConfigurationCommandApplicationService;
import com.aiplatform.identity.application.IamConfigurationQueryApplicationService;
import com.aiplatform.identity.application.command.ChangeIamTenantStatusCommand;
import com.aiplatform.identity.application.command.UpdateIamTenantFeatureCommand;
import com.aiplatform.identity.presentation.controller.PlatformTenantController;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformTenantControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void capabilityChangeCarriesRequiredReasonIntoCommand() throws Exception {
        Fixture fixture = fixture("TRACE-IAM-FEATURE-001");

        fixture.mockMvc().perform(put("/api/v1/iam/tenants/2001/features/knowledge-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"租户已完成知识库下线评审\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-FEATURE-001"));

        ArgumentCaptor<UpdateIamTenantFeatureCommand> captor = ArgumentCaptor.forClass(UpdateIamTenantFeatureCommand.class);
        verify(fixture.commandService()).updateTenantFeature(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().featureCode()).isEqualTo("knowledge-base");
        assertThat(captor.getValue().enabled()).isFalse();
        assertThat(captor.getValue().reason()).isEqualTo("租户已完成知识库下线评审");
    }

    @Test
    void statusChangeCarriesRequiredReasonIntoCommand() throws Exception {
        Fixture fixture = fixture("TRACE-IAM-STATUS-001");

        fixture.mockMvc().perform(post("/api/v1/iam/tenants/2001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"合同到期并已完成停服通知\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-STATUS-001"));

        ArgumentCaptor<ChangeIamTenantStatusCommand> captor = ArgumentCaptor.forClass(ChangeIamTenantStatusCommand.class);
        verify(fixture.commandService()).changeTenantStatus(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().status()).isEqualTo("DISABLED");
        assertThat(captor.getValue().reason()).isEqualTo("合同到期并已完成停服通知");
    }

    @Test
    void highRiskTenantChangesRejectMissingReasonBeforeApplicationUse() throws Exception {
        Fixture fixture = fixture("TRACE-IAM-REASON-REQUIRED");

        fixture.mockMvc().perform(put("/api/v1/iam/tenants/2001/features/knowledge-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        fixture.mockMvc().perform(post("/api/v1/iam/tenants/2001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fixture.commandService());
    }

    private static Fixture fixture(String traceId) {
        TraceContext.ensureTraceId(traceId);
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("1"), "platform-admin", "1001"));
        IamConfigurationQueryApplicationService queryService = mock(IamConfigurationQueryApplicationService.class);
        IamConfigurationCommandApplicationService commandService = mock(IamConfigurationCommandApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PlatformTenantController(queryService, commandService)).build();
        return new Fixture(mockMvc, commandService);
    }

    private record Fixture(MockMvc mockMvc, IamConfigurationCommandApplicationService commandService) {
    }
}
