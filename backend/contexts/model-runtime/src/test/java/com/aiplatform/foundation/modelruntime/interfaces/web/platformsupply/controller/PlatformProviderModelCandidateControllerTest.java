package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ConfirmPlatformProviderModelCandidateCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelCandidateConfirmationService;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelCandidateReadService;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PlatformProviderModelCandidateControllerTest {

    @Test
    void 候选搜索传递规范词组并拒绝范围或重复参数() throws Exception {
        var reads = mock(PlatformProviderModelCandidateReadService.class);
        var confirmations = mock(PlatformProviderModelCandidateConfirmationService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderModelCandidateController(reads, confirmations))
                .setCustomArgumentResolvers(platformContext(context)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/platform/model-supply/connections/320000000000000001/model-candidates")
                .param("expectedConnectionVersion", "3").param("q", " 中文%_\\ ").param("pageNumber", "2").param("pageSize", "10"))
                .andExpect(status().isOk());
        var query = ArgumentCaptor.forClass(com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderModelCandidatesQuery.class);
        verify(reads).list(query.capture());
        org.junit.jupiter.api.Assertions.assertEquals("中文%_\\", query.getValue().q());
        org.junit.jupiter.api.Assertions.assertEquals(3, query.getValue().expectedConnectionVersion());
        org.junit.jupiter.api.Assertions.assertEquals(2, query.getValue().page().pageNumber());
        for (var key : java.util.List.of("tenantId", "scope", "unknown")) {
            org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class,
                    () -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                            "/api/v1/platform/model-supply/connections/320000000000000001/model-candidates")
                            .param("expectedConnectionVersion", "3").param(key, "100")));
        }
        org.mockito.Mockito.verifyNoMoreInteractions(reads); org.mockito.Mockito.verifyNoInteractions(confirmations);
    }

    @Test
    void 管理员确认只接收平台契约并返回安全候选() throws Exception {
        var reads = mock(PlatformProviderModelCandidateReadService.class);
        var confirmations = mock(PlatformProviderModelCandidateConfirmationService.class);
        var context = mock(PlatformRequestContext.class);
        when(confirmations.confirm(any())).thenReturn(new PlatformProviderModelCandidateView(
                "model-from-provider", "EMBEDDING", java.util.Set.of("EMBEDDING"), "CONFIRMED_BY_ADMIN"));
        var mvc = MockMvcBuilders.standaloneSetup(
                        new PlatformProviderModelCandidateController(reads, confirmations))
                .setCustomArgumentResolvers(platformContext(context))
                .build();

        mvc.perform(post("/api/v1/platform/model-supply/connections/320000000000000001/model-candidates/confirmation")
                        .contentType("application/json")
                        .content("{\"expectedConnectionVersion\":1,\"modelCode\":\"model-from-provider\","
                                + "\"modelType\":\"EMBEDDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("CONFIRMED_BY_ADMIN"))
                .andExpect(jsonPath("$.modelType").value("EMBEDDING"))
                .andExpect(jsonPath("$.rawResponse").doesNotExist())
                .andExpect(jsonPath("$.internalId").doesNotExist());

        var command = ArgumentCaptor.forClass(ConfirmPlatformProviderModelCandidateCommand.class);
        verify(confirmations).confirm(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals("model-from-provider", command.getValue().modelCode());
    }

    private static HandlerMethodArgumentResolver platformContext(PlatformRequestContext context) {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(PlatformRequestContext.class);
            }
            @Override public Object resolveArgument(
                    MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return context;
            }
        };
    }
}
