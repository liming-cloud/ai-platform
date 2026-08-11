package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformAccountEstablishmentApplicationService;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAccountEstablishmentControllerTest {

    @Test
    void HTTP建立账户只接受登录名和初始口令并从解析器恢复平台上下文() throws Exception {
        var service = mock(PlatformAccountEstablishmentApplicationService.class);
        when(service.establish(any(), any(), any())).thenReturn(new PlatformAccountSummary(
                4921825928971026436L, "new-account", "ACTIVE", 0L, Instant.parse("2026-08-11T04:00:00Z"), Set.of()));
        var context = context();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new PlatformAccountEstablishmentController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/platform/accounts")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"new-account\",\"password\":\"initial-password\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                        .contains("\"subjectId\":\"4921825928971026436\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.loginName").value("new-account"));

        verify(service).establish(org.mockito.Mockito.same(context), org.mockito.Mockito.eq("new-account"), any(char[].class));
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
                0L,
                Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_platform_account_establishment");
    }
}
