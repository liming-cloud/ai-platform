package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.DiscoverPlatformProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.DiscoverPlatformProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
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

class PlatformProviderModelDiscoveryControllerTest {

    @Test
    void 平台模型发现只从平台会话恢复身份且不回显连接秘密() throws Exception {
        var service = mock(PlatformProviderModelDiscoveryService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderModelDiscoveryController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.discover(any())).thenReturn(new DiscoverPlatformProviderModelsResult(
                "620000000000000001",
                true,
                Set.of(PlatformDiscoveredModelCandidate.confirmedByProvider(
                        "deterministic-embedding", ModelType.EMBEDDING, Set.of(ModelCapability.EMBEDDING))),
                "DETERMINISTIC_DISCOVERY_OK",
                Duration.ZERO,
                Instant.parse("2026-08-13T02:00:00Z"),
                Instant.parse("2026-08-13T03:00:00Z")));

        mvc.perform(post("/api/v1/platform/model-supply/connections/610000000000000001/model-discovery")
                        .param("tenantId", "100000000000000099")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1,\"timeoutMillis\":3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.models[0].modelCode").value("deterministic-embedding"))
                .andExpect(jsonPath("$.discoveryId").doesNotExist())
                .andExpect(jsonPath("$.resultCode").doesNotExist())
                .andExpect(jsonPath("$.elapsed").doesNotExist())
                .andExpect(jsonPath("$.discoveredAt").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        var command = ArgumentCaptor.forClass(DiscoverPlatformProviderModelsCommand.class);
        verify(service).discover(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(
                "610000000000000001", command.getValue().providerConnectionId());
    }

    @Test
    void 并发冲突只返回安全业务状态且不泄露错误码或内部标识() throws Exception {
        var service = mock(PlatformProviderModelDiscoveryService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderModelDiscoveryController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.discover(any())).thenThrow(new ProviderConnectionConcurrentModificationException());

        mvc.perform(post("/api/v1/platform/model-supply/connections/610000000000000001/model-discovery")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0,\"timeoutMillis\":3000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.succeeded").value(false))
                .andExpect(jsonPath("$.models").isEmpty())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    private static HandlerMethodArgumentResolver platformContext(PlatformRequestContext context) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(PlatformRequestContext.class);
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
}
