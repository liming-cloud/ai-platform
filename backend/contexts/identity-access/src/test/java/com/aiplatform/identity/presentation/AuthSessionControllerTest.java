package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.IdentitySessionApplicationService;
import com.aiplatform.identity.application.LoginApplicationService;
import com.aiplatform.identity.application.command.CurrentUserCommand;
import com.aiplatform.identity.application.command.LogoutCommand;
import com.aiplatform.identity.application.result.CurrentUserMenuResult;
import com.aiplatform.identity.application.result.CurrentUserResult;
import com.aiplatform.identity.presentation.controller.LoginController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthSessionControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
    }

    @Test
    void currentUserReadsTenantFromContextAndAuthorizationHeader() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-ME-001");
        TenantContext.set(TenantId.of("2001"));
        IdentitySessionApplicationService sessionService = mock(IdentitySessionApplicationService.class);
        when(sessionService.currentUser(any(CurrentUserCommand.class))).thenReturn(new CurrentUserResult(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin", "auditor"),
                Set.of(new PermissionCode("model-hub:provider:read"), new PermissionCode("knowledge-base:retrieval:query")),
                List.of(
                        new CurrentUserMenuResult(
                                "business.knowledge-bases",
                                "知识库",
                                "BUSINESS",
                                "/workspace/knowledge-bases",
                                "BusinessKnowledgeBases",
                                "business/knowledge-bases/list",
                                "knowledge-base:retrieval:query",
                                10
                        ),
                        new CurrentUserMenuResult(
                                "admin.model-providers",
                                "模型",
                                "ADMIN",
                                "/admin/model-providers",
                                "AdminModelProviders",
                                "admin/model-providers/list",
                                "model-hub:provider:read",
                                20
                        )
                ),
                "/workspace/knowledge-bases",
                Instant.parse("2026-05-22T03:30:00Z")
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(
                mock(LoginApplicationService.class),
                sessionService
        )).build();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer token-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-ME-001"))
                .andExpect(jsonPath("$.body.tenantId").value("2001"))
                .andExpect(jsonPath("$.body.userId").value("1001"))
                .andExpect(jsonPath("$.body.loginName").value("admin"))
                .andExpect(jsonPath("$.body.roleCodes[0]").value("auditor"))
                .andExpect(jsonPath("$.body.roleCodes[1]").value("platform_admin"))
                .andExpect(jsonPath("$.body.permissionCodes[0]").value("knowledge-base:retrieval:query"))
                .andExpect(jsonPath("$.body.permissionCodes[1]").value("model-hub:provider:read"))
                .andExpect(jsonPath("$.body.menus[0].componentKey").value("business/knowledge-bases/list"))
                .andExpect(jsonPath("$.body.menus[1].componentKey").value("admin/model-providers/list"))
                .andExpect(jsonPath("$.body.defaultRoute").value("/workspace/knowledge-bases"));

        ArgumentCaptor<CurrentUserCommand> commandCaptor = ArgumentCaptor.forClass(CurrentUserCommand.class);
        verify(sessionService).currentUser(commandCaptor.capture());
        CurrentUserCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.tokenValue()).isEqualTo("token-1001");
        assertThat(command.traceId()).isEqualTo("TRACE-FILTER-ME-001");
    }

    @Test
    void logoutReadsTenantFromContextAndAuthorizationHeader() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-LOGOUT-001");
        TenantContext.set(TenantId.of("2001"));
        IdentitySessionApplicationService sessionService = mock(IdentitySessionApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(
                mock(LoginApplicationService.class),
                sessionService
        )).build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "token-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-LOGOUT-001"));

        ArgumentCaptor<LogoutCommand> commandCaptor = ArgumentCaptor.forClass(LogoutCommand.class);
        verify(sessionService).logout(commandCaptor.capture());
        LogoutCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.tokenValue()).isEqualTo("token-1001");
        assertThat(command.traceId()).isEqualTo("TRACE-FILTER-LOGOUT-001");
    }
}
