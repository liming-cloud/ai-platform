package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformRoleBindingView;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformRoleBindingApplicationService;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformRoleBindingControllerTest {

    @Test
    void HTTP只从路径读取目标账户且请求体只能包含角色代码() throws Exception {
        var service = mock(PlatformRoleBindingApplicationService.class);
        var context = accountAdministrator();
        when(service.bind(any(), any(Long.class), any())).thenReturn(new PlatformRoleBindingView(
                3001L, 102L, "PLATFORM_ADMINISTRATOR", "ACTIVE", 0L,
                Instant.parse("2026-08-11T06:00:00Z"), null));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new PlatformRoleBindingController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/accounts/102/role-bindings")
                        .contentType("application/json")
                        .content("{\"role\":\"PLATFORM_ADMINISTRATOR\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.roleCode").value("PLATFORM_ADMINISTRATOR"));

        verify(service).bind(same(context), org.mockito.Mockito.eq(102L), org.mockito.Mockito.eq("PLATFORM_ADMINISTRATOR"));
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

    private static PlatformRequestContext accountAdministrator() {
        return new PlatformRequestContext(
                new SubjectId(101L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_platform_role_binding");
    }
}
