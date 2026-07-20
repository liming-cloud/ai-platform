package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.IamConfigurationCommandApplicationService;
import com.aiplatform.identity.application.command.CreateIamMenuCommand;
import com.aiplatform.identity.application.command.ReplaceIamRoleMenusCommand;
import com.aiplatform.identity.application.command.ReplaceIamRolePermissionsCommand;
import com.aiplatform.identity.presentation.controller.IamConfigurationCommandController;
import com.aiplatform.shared.kernel.TenantContext;
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

class IamConfigurationCommandControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void menuCreateReadsTenantPrincipalAndTraceContext() throws Exception {
        TraceContext.ensureTraceId("TRACE-IAM-SAVE-007");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        IamConfigurationCommandApplicationService service = mock(IamConfigurationCommandApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationCommandController(service)).build();
        when(service.createMenu(org.mockito.ArgumentMatchers.any(CreateIamMenuCommand.class))).thenReturn("5010");

        mockMvc.perform(post("/api/v1/iam/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "admin.audit-log",
                                  "name": "审计日志",
                                  "scope": "ADMIN",
                                  "path": "/admin/audit-log",
                                  "routeName": "AdminAuditLog",
                                  "componentKey": "admin/audit-log",
                                  "icon": "audit",
                                  "requiredPermissionCode": "identity-access:iam-menu:manage",
                                  "sort": 60,
                                  "status": "ACTIVE",
                                  "featureCodes": ["identity-settings"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-SAVE-007"))
                .andExpect(jsonPath("$.body.resourceId").value("5010"))
                .andExpect(jsonPath("$.body.status").value("SAVED"));

        ArgumentCaptor<CreateIamMenuCommand> commandCaptor = ArgumentCaptor.forClass(CreateIamMenuCommand.class);
        verify(service).createMenu(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(commandCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(commandCaptor.getValue().traceId()).isEqualTo("TRACE-IAM-SAVE-007");
        assertThat(commandCaptor.getValue().code()).isEqualTo("admin.audit-log");
        assertThat(commandCaptor.getValue().scope()).isEqualTo("ADMIN");
        assertThat(commandCaptor.getValue().featureCodes()).containsExactly("identity-settings");
    }

    @Test
    void roleMenuSaveReadsTenantPrincipalAndTraceContext() throws Exception {
        TraceContext.ensureTraceId("TRACE-IAM-SAVE-006");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        IamConfigurationCommandApplicationService service = mock(IamConfigurationCommandApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationCommandController(service)).build();

        mockMvc.perform(put("/api/v1/iam/roles/3002/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuIds\":[\"5002\",\"5003\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-SAVE-006"))
                .andExpect(jsonPath("$.body.resourceId").value("3002"))
                .andExpect(jsonPath("$.body.status").value("SAVED"));

        ArgumentCaptor<ReplaceIamRoleMenusCommand> commandCaptor = ArgumentCaptor.forClass(ReplaceIamRoleMenusCommand.class);
        verify(service).replaceRoleMenus(commandCaptor.capture());
        assertThat(commandCaptor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(commandCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(commandCaptor.getValue().traceId()).isEqualTo("TRACE-IAM-SAVE-006");
        assertThat(commandCaptor.getValue().roleId()).isEqualTo("3002");
        assertThat(commandCaptor.getValue().menuIds()).containsExactly("5002", "5003");
    }

    @Test
    void rolePermissionReplacementCarriesRequiredReasonIntoCommand() throws Exception {
        TraceContext.ensureTraceId("TRACE-IAM-PERMISSION-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        IamConfigurationCommandApplicationService service = mock(IamConfigurationCommandApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationCommandController(service)).build();

        mockMvc.perform(put("/api/v1/iam/roles/3002/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionCodes": ["knowledge-base:retrieval:query"],
                                  "reason": "按岗位职责替换完整权限集合"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-PERMISSION-001"));

        ArgumentCaptor<ReplaceIamRolePermissionsCommand> commandCaptor = ArgumentCaptor.forClass(ReplaceIamRolePermissionsCommand.class);
        verify(service).replaceRolePermissions(commandCaptor.capture());
        assertThat(commandCaptor.getValue().roleId()).isEqualTo("3002");
        assertThat(commandCaptor.getValue().permissionCodes()).containsExactly("knowledge-base:retrieval:query");
        assertThat(commandCaptor.getValue().reason()).isEqualTo("按岗位职责替换完整权限集合");
    }

    @Test
    void rolePermissionReplacementRejectsMissingReasonBeforeApplicationUse() throws Exception {
        TraceContext.ensureTraceId("TRACE-IAM-PERMISSION-002");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        IamConfigurationCommandApplicationService service = mock(IamConfigurationCommandApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationCommandController(service)).build();

        mockMvc.perform(put("/api/v1/iam/roles/3002/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"knowledge-base:retrieval:query\"]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
