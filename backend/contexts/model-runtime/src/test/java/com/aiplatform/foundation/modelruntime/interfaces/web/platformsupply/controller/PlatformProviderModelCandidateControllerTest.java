package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelCandidateView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderModelCandidatesQuery;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelCandidateReadService;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PlatformProviderModelCandidateControllerTest {

    @Test
    void 平台候选列表从平台会话读取身份并使用服务端分页() throws Exception {
        var service = mock(PlatformProviderModelCandidateReadService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderModelCandidateController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.list(any())).thenReturn(PageResult.of(
                java.util.List.of(new PlatformProviderModelCandidateView(
                        "deterministic-embedding", "EMBEDDING", Set.of("EMBEDDING"))),
                new PageQuery(2, 1), 3));

        mvc.perform(get("/api/v1/platform/model-supply/connections/610000000000000001/model-candidates")
                        .param("expectedConnectionVersion", "1")
                        .param("pageNumber", "2")
                        .param("pageSize", "1")
                        .param("tenantId", "100000000000000099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].modelCode").value("deterministic-embedding"))
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist());

        var query = ArgumentCaptor.forClass(ListPlatformProviderModelCandidatesQuery.class);
        verify(service).list(query.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, query.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals("610000000000000001", query.getValue().providerConnectionId());
        org.junit.jupiter.api.Assertions.assertEquals(1L, query.getValue().expectedConnectionVersion());
        org.junit.jupiter.api.Assertions.assertEquals(new PageQuery(2, 1), query.getValue().page());
    }

    private static HandlerMethodArgumentResolver platformContext(PlatformRequestContext context) {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(PlatformRequestContext.class);
            }

            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return context;
            }
        };
    }
}
