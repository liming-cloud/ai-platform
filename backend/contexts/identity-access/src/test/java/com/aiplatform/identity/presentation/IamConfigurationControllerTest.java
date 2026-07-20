package com.aiplatform.identity.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.identity.application.IamConfigurationQueryApplicationService;
import com.aiplatform.identity.application.result.IamMenuConfigurationResult;
import com.aiplatform.identity.application.result.IamPermissionResult;
import com.aiplatform.identity.application.result.IamRoleResult;
import com.aiplatform.identity.application.result.IamTenantFeatureResult;
import com.aiplatform.identity.presentation.controller.IamConfigurationController;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IamConfigurationControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
    }

    @Test
    void listConfigurationReadsTenantFromContext() throws Exception {
        TraceContext.ensureTraceId("TRACE-IAM-CONFIG-001");
        TenantContext.set(TenantId.of("2001"));
        IamConfigurationQueryApplicationService service = mock(IamConfigurationQueryApplicationService.class);
        when(service.listMenus(TenantId.of("2001"))).thenReturn(List.of(menu()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationController(service)).build();

        mockMvc.perform(get("/api/v1/iam/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-IAM-CONFIG-001"))
                .andExpect(jsonPath("$.body[0].code").value("admin.system-settings"))
                .andExpect(jsonPath("$.body[0].featureCodes[0]").value("identity-settings"))
                .andExpect(jsonPath("$.body[0].tenantFeatureEnabled").value(true));

        ArgumentCaptor<TenantId> tenantCaptor = ArgumentCaptor.forClass(TenantId.class);
        verify(service).listMenus(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue()).isEqualTo(TenantId.of("2001"));
    }

    @Test
    void readsRoleMenuAndPermissionMatrices() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        IamConfigurationQueryApplicationService service = mock(IamConfigurationQueryApplicationService.class);
        when(service.listTenantFeatures(TenantId.of("2001"))).thenReturn(List.of(feature()));
        when(service.listRoles(TenantId.of("2001"))).thenReturn(List.of(role()));
        when(service.listPermissions(TenantId.of("2001"))).thenReturn(List.of(permission()));
        when(service.listRoleMenus(TenantId.of("2001"), "3001")).thenReturn(List.of(menu()));
        when(service.listRolePermissions(TenantId.of("2001"), "3001")).thenReturn(List.of(permission()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new IamConfigurationController(service)).build();

        mockMvc.perform(get("/api/v1/iam/tenant-features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].featureCode").value("identity-settings"));
        mockMvc.perform(get("/api/v1/iam/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].code").value("platform-admin"));
        mockMvc.perform(get("/api/v1/iam/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].code").value("identity-access:iam-config:read"));
        mockMvc.perform(get("/api/v1/iam/roles/3001/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].code").value("admin.system-settings"));
        mockMvc.perform(get("/api/v1/iam/roles/3001/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0].code").value("identity-access:iam-config:read"));
    }

    private static IamMenuConfigurationResult menu() {
        return new IamMenuConfigurationResult(
                "5009",
                "admin.system-settings",
                "系统设置",
                "ADMIN",
                "/admin/system-settings",
                "AdminSystemSettings",
                "admin/system-settings",
                "setting",
                "identity-access:system-setting:manage",
                50,
                "ACTIVE",
                true,
                List.of("identity-settings"),
                true
        );
    }

    private static IamTenantFeatureResult feature() {
        return new IamTenantFeatureResult("identity-settings", "身份与租户设置", true, "PLAN");
    }

    private static IamRoleResult role() {
        return new IamRoleResult("3001", "platform-admin", "平台管理员", "PLATFORM", "admin.platform-overview", "ACTIVE", null);
    }

    private static IamPermissionResult permission() {
        return new IamPermissionResult("4003", "identity-access:iam-config:read", "读取 IAM 配置", "iam-config", "read", null, "ACTIVE");
    }
}
