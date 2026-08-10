package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleView;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleDirectoryApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleLifecycleApplicationService;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantCustomRoleLifecycleControllerTest {

    @Test
    void 应从路径和严格请求变更当前租户角色权限() {
        var lifecycle = mock(TenantCustomRoleLifecycleApplicationService.class);
        when(lifecycle.changePermissions(any(), any())).thenReturn(role());
        var controller = new TenantCustomRoleController(mock(TenantCustomRoleApplicationService.class), mock(TenantCustomRoleDirectoryApplicationService.class), lifecycle);

        var response = controller.changePermissions(context(), 701L, Map.of("permissions", List.of("assistant:query")));

        assertThat(response.getBody()).extracting("id", "permissionCodes")
                .containsExactly("701", List.of("assistant:query"));
        verify(lifecycle).changePermissions(any(), any());
    }

    @Test
    void 应停用当前租户角色且不从请求接收租户或主体() {
        var lifecycle = mock(TenantCustomRoleLifecycleApplicationService.class);
        when(lifecycle.disable(any(), any())).thenReturn(role());
        var controller = new TenantCustomRoleController(mock(TenantCustomRoleApplicationService.class), mock(TenantCustomRoleDirectoryApplicationService.class), lifecycle);

        var response = controller.disable(context(), 701L);

        assertThat(response.getBody()).extracting("status").isEqualTo("DISABLED");
        verify(lifecycle).disable(any(), any());
    }

    @Test
    void HTTP入口只从路径读取角色标识且从解析器恢复可信上下文() throws Exception {
        var lifecycle = mock(TenantCustomRoleLifecycleApplicationService.class);
        when(lifecycle.changePermissions(any(), any())).thenReturn(role());
        var controller = new TenantCustomRoleController(mock(TenantCustomRoleApplicationService.class), mock(TenantCustomRoleDirectoryApplicationService.class), lifecycle);
        var context = context();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/tenant/custom-roles/701/permissions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"assistant:query\"]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value("701"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.permissionCodes[0]").value("assistant:query"));

        verify(lifecycle).changePermissions(org.mockito.Mockito.same(context), any());
    }

    private static org.springframework.web.method.support.HandlerMethodArgumentResolver trustedContext(
            TrustedRequestContext context) {
        return new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                return parameter.getParameterType().equals(TrustedRequestContext.class);
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

    private static TenantCustomRoleView role() {
        return new TenantCustomRoleView(701L, "问答专员", Set.of("assistant:query"), 2L, "DISABLED");
    }

    private static TrustedRequestContext context() {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(100000000000000002L),
                new MembershipId(100000000000000003L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                1L,
                Set.of(new PermissionCode("access:manage")),
                "trc_custom_role_lifecycle");
    }
}
