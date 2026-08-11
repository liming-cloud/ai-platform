package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountGovernanceApplicationService;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountDirectoryApplicationService;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.util.Set;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformAccountGovernanceControllerTest {

    @Test
    void HTTP目录从URL读取分页且不接收平台身份字段() throws Exception {
        var service = mock(PlatformAccountGovernanceApplicationService.class);
        var directory = mock(PlatformAccountDirectoryApplicationService.class);
        when(directory.list(any(), any())).thenReturn(new com.aiplatform.foundation.shared.contract.paging.PageResult<>(
                java.util.List.of(new PlatformAccountSummary(102L, "target-account", "ACTIVE", 0L, Instant.parse("2026-08-11T00:00:00Z"), java.util.Set.of())),
                2L, 20L, 21L, 2L, true, false));
        var context = context();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new PlatformAccountGovernanceController(service, directory))
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/platform/accounts?pageNumber=2&pageSize=20"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].loginName").value("target-account"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.pageNumber").value(2));

        verify(directory).list(org.mockito.Mockito.same(context), any());
    }

    @Test
    void HTTP入口只从路径读取目标账户且从解析器恢复平台上下文() throws Exception {
        var service = mock(PlatformAccountGovernanceApplicationService.class);
        var controller = new PlatformAccountGovernanceController(service, mock(PlatformAccountDirectoryApplicationService.class));
        var context = context();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/accounts/102/disable"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());

        verify(service).disable(org.mockito.Mockito.same(context), org.mockito.Mockito.eq(102L));
    }

    @Test
    void HTTP启用入口只从路径读取目标账户且从解析器恢复平台上下文() throws Exception {
        var service = mock(PlatformAccountGovernanceApplicationService.class);
        var controller = new PlatformAccountGovernanceController(service, mock(PlatformAccountDirectoryApplicationService.class));
        var context = context();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/accounts/102/enable"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());

        verify(service).enable(org.mockito.Mockito.same(context), org.mockito.Mockito.eq(102L));
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
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_platform_account");
    }
}
