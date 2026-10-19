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
    void 实际读取用例将冻结名称与选择编码分别投影到HTTP() throws Exception {
        var repository = mock(com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository.class);
        var lookup = mock(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup.class);
        var connection = mock(com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection.class);
        var id = com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId.parse("320000000000000001");
        var context = mock(PlatformRequestContext.class);
        when(context.hasPermission(com.aiplatform.foundation.security.identity.PlatformPermissions.MODEL_PLATFORM_WRITE)).thenReturn(true);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(connection));
        when(connection.id()).thenReturn(id);
        when(connection.version()).thenReturn(3L);
        when(connection.status()).thenReturn(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus.VERIFIED);
        var now = java.time.Instant.parse("2026-10-19T08:00:00Z");
        when(lookup.findCurrentCandidates(any(), org.mockito.ArgumentMatchers.eq(3L), any(), any(), any()))
                .thenReturn(com.aiplatform.foundation.shared.contract.paging.PageResult.of(java.util.List.of(
                        com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate
                                .requiresAdministratorConfirmation("ep-synthetic-opaque-code", "独立受控模型名称")),
                        new com.aiplatform.foundation.shared.contract.paging.PageQuery(1, 20), 1));
        var reads = new PlatformProviderModelCandidateReadService(repository, lookup,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        var confirmations = mock(PlatformProviderModelCandidateConfirmationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderModelCandidateController(reads, confirmations))
                .setCustomArgumentResolvers(platformContext(context)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/platform/model-supply/connections/320000000000000001/model-candidates")
                        .param("expectedConnectionVersion", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("独立受控模型名称"))
                .andExpect(jsonPath("$.items[0].modelCode").value("ep-synthetic-opaque-code"))
                .andExpect(jsonPath("$.items[0].classification").value("REQUIRES_ADMIN_CONFIRMATION"));
        org.mockito.Mockito.verifyNoInteractions(confirmations);
    }

    @Test
    void 历史候选缺少名称时返回明确缺失而不是将供应商编码当作名称() throws Exception {
        var reads = mock(PlatformProviderModelCandidateReadService.class);
        var confirmations = mock(PlatformProviderModelCandidateConfirmationService.class);
        var context = mock(PlatformRequestContext.class);
        when(reads.list(any())).thenReturn(com.aiplatform.foundation.shared.contract.paging.PageResult.of(
                java.util.List.of(new PlatformProviderModelCandidateView(
                        "ep-synthetic-opaque-code", null, java.util.Set.of(), "REQUIRES_ADMIN_CONFIRMATION")),
                new com.aiplatform.foundation.shared.contract.paging.PageQuery(1, 20), 1));
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderModelCandidateController(reads, confirmations))
                .setCustomArgumentResolvers(platformContext(context)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/platform/model-supply/connections/320000000000000001/model-candidates")
                        .param("expectedConnectionVersion", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("名称暂未提供"))
                .andExpect(jsonPath("$.items[0].modelCode").value("ep-synthetic-opaque-code"));
    }

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
