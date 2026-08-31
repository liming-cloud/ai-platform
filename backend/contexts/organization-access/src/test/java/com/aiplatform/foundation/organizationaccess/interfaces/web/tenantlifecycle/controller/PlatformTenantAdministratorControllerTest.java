package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorSummary;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorView;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorApplicationService;
import com.aiplatform.foundation.organizationaccess.application.member.service.PlatformTenantAdministratorQueryService;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformTenantAdministratorControllerTest {

    @Test
    void HTTP目录和候选只交付受控业务摘要() throws Exception {
        var commands = mock(PlatformTenantAdministratorApplicationService.class);
        var queries = mock(PlatformTenantAdministratorQueryService.class);
        var context = context();
        when(queries.list(any(), any(), any())).thenReturn(new PageResult<>(
                List.of(new TenantAdministratorSummary(
                        "701", "tenant-admin", "ENABLED", 2L, 4L,
                        Instant.parse("2026-08-31T00:00:00Z"))),
                1L, 20L, 1L, 1L, false, false));
        when(queries.candidates(any(), any(), any(), any())).thenReturn(new PageResult<>(
                List.of(new PlatformAccountCandidate("801", "next-admin", "ACTIVE")),
                1L, 20L, 1L, 1L, false, false));
        var mvc = MockMvcBuilders
                .standaloneSetup(new PlatformTenantAdministratorController(commands, queries))
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        var directory = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/platform/tenants/tenant-a/administrators?pageNumber=1&pageSize=20"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].loginName").value("tenant-admin"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(directory.contains("\"membershipReference\":\"701\""));
        assertFalse(directory.contains("password"));
        assertFalse(directory.contains("subjectId"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/platform/tenants/tenant-a/administrators/candidates?q=next&pageNumber=1&pageSize=20"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.items[0].loginName").value("next-admin"));

        verify(queries).list(org.mockito.Mockito.same(context), org.mockito.Mockito.eq("tenant-a"), any());
        verify(queries).candidates(
                org.mockito.Mockito.same(context), org.mockito.Mockito.eq("tenant-a"),
                org.mockito.Mockito.eq("next"), any());
    }

    @Test
    void HTTP写操作严格绑定路径租户和受控资源引用() throws Exception {
        var commands = mock(PlatformTenantAdministratorApplicationService.class);
        var queries = mock(PlatformTenantAdministratorQueryService.class);
        var context = context();
        var view = new TenantAdministratorView(
                701L, "tenant-admin", "TENANT_ADMINISTRATOR", "ENABLED", 2L, 4L,
                Instant.parse("2026-08-31T00:00:00Z"));
        when(commands.assign(any(), any(), any(Long.class))).thenReturn(view);
        when(commands.disable(any(), any(), any(Long.class), any(Long.class))).thenReturn(view);
        when(commands.restore(any(), any(), any(Long.class), any(Long.class))).thenReturn(view);
        when(commands.replace(any(), any(), any(Long.class), any(Long.class), any(Long.class))).thenReturn(view);
        var mvc = MockMvcBuilders
                .standaloneSetup(new PlatformTenantAdministratorController(commands, queries))
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/tenants/tenant-a/administrators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountReference\":\"801\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.membershipReference").value("701"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/tenants/tenant-a/administrators/701/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":4}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/tenants/tenant-a/administrators/701/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":4}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/tenants/tenant-a/administrators/701/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":4,\"replacementAccountReference\":\"801\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        verify(commands).assign(context, "tenant-a", 801L);
        verify(commands).disable(context, "tenant-a", 701L, 4L);
        verify(commands).restore(context, "tenant-a", 701L, 4L);
        verify(commands).replace(context, "tenant-a", 701L, 4L, 801L);
    }

    private static org.springframework.web.method.support.HandlerMethodArgumentResolver platformContext(
            PlatformRequestContext context) {
        return new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                return parameter.getParameterType().equals(PlatformRequestContext.class);
            }

            @Override
            public Object resolveArgument(
                    org.springframework.core.MethodParameter parameter,
                    org.springframework.web.method.support.ModelAndViewContainer container,
                    org.springframework.web.context.request.NativeWebRequest request,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return context;
            }
        };
    }

    private static PlatformRequestContext context() {
        return new PlatformRequestContext(
                new SubjectId(101L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                2L,
                Set.of(
                        PlatformPermissions.SESSION_ACCESS,
                        PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_platform_tenant_administrator");
    }
}
