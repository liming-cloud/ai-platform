package com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.provider.command.DiscoverProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.DiscoverProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ProviderConnectionControllerTest {

    @Test
    void 模型发现HTTP入口只从可信上下文取身份且不回显连接地址() throws Exception {
        var connections = mock(ProviderConnectionService.class);
        var discoveries = mock(ProviderModelDiscoveryService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ProviderConnectionController(connections, discoveries);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(discoveries.discover(any())).thenReturn(discoveryResult());

        mvc.perform(post("/api/v1/model-providers/310000000000000001/model-discovery")
                        .param("tenantId", "100000000000000099")
                        .param("subjectId", "4921825928971026434")
                        .contentType("application/json")
                        .content("{" + "\"expectedVersion\":1,\"timeoutMillis\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.models[0].modelCode").value("deterministic-chat"))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist());

        var command = ArgumentCaptor.forClass(DiscoverProviderModelsCommand.class);
        verify(discoveries).discover(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(
                "310000000000000001", command.getValue().providerConnectionId());
        org.junit.jupiter.api.Assertions.assertEquals(1, command.getValue().expectedVersion());
        org.junit.jupiter.api.Assertions.assertEquals(Duration.ofSeconds(2), command.getValue().timeout());
    }

    private static HandlerMethodArgumentResolver trustedContext(TrustedRequestContext context) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(TrustedRequestContext.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer container,
                    NativeWebRequest request,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return context;
            }
        };
    }

    private static DiscoverProviderModelsResult discoveryResult() {
        return new DiscoverProviderModelsResult(
                "340000000000000001",
                true,
                Set.of(new DiscoveredModel(
                        "deterministic-chat", ModelType.CHAT, Set.of(ModelCapability.CHAT))),
                "DETERMINISTIC_DISCOVERY_OK",
                Duration.ofMillis(10),
                Instant.parse("2026-08-10T03:00:00Z"),
                Instant.parse("2026-08-11T03:00:00Z"));
    }
}
