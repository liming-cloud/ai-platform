package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.platformsupply.command.CreatePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.CreatePlatformProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderConnectionView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.query.ListPlatformProviderConnectionsQuery;
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionService;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PlatformProviderConnectionControllerTest {

    @Test
    void 平台连接HTTP只从平台会话取身份且不回显秘密或端点() throws Exception {
        var service = mock(PlatformProviderConnectionService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.create(any())).thenReturn(new CreatePlatformProviderConnectionResult(
                view(), false));

        mvc.perform(post("/api/v1/platform/model-supply/connections")
                        .param("tenantId", "100000000000000099")
                        .header("Idempotency-Key", "platform-request-0001")
                        .contentType("application/json")
                        .content("{" +
                                "\"providerKind\":\"DETERMINISTIC\",\"displayName\":\"平台模型\","
                                + "\"discoveryProfile\":\"DETERMINISTIC_CATALOG\","
                                + "\"endpoint\":\"memory://deterministic\",\"region\":\"本地\","
                                + "\"authenticationType\":\"API_KEY\",\"apiKey\":\"test-api-key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        var command = ArgumentCaptor.forClass(CreatePlatformProviderConnectionCommand.class);
        verify(service).create(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile
                        .DETERMINISTIC_CATALOG,
                command.getValue().discoveryProfile());
        org.junit.jupiter.api.Assertions.assertEquals("[已脱敏]", command.getValue().apiKey().toString());
    }

    @Test
    void 方舟连接HTTP必须接收独立AKSK且响应不回显任何凭据() throws Exception {
        var service = mock(PlatformProviderConnectionService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.create(any())).thenReturn(new CreatePlatformProviderConnectionResult(view(), false));

        mvc.perform(post("/api/v1/platform/model-supply/connections")
                        .header("Idempotency-Key", "platform-ark-request-0001")
                        .contentType("application/json")
                        .content("{"+
                                "\"providerKind\":\"OPENAI_COMPATIBLE\",\"displayName\":\"火山方舟\","+
                                "\"discoveryProfile\":\"VOLCENGINE_ARK_CONTROL_PLANE\","+
                                "\"endpoint\":\"https://ark.cn-beijing.volces.com/api/v3\","+
                                "\"region\":\"cn-beijing\",\"authenticationType\":\"API_KEY\","+
                                "\"apiKey\":\"inference-secret\",\"discoveryAccessKeyId\":\"AK_TEST\","+
                                "\"discoverySecretAccessKey\":\"SK_TEST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.discoveryAccessKeyId").doesNotExist())
                .andExpect(jsonPath("$.discoverySecretAccessKey").doesNotExist())
                .andExpect(jsonPath("$.endpoint").doesNotExist());

        var command = ArgumentCaptor.forClass(CreatePlatformProviderConnectionCommand.class);
        verify(service).create(command.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(command.getValue().discoveryAccessKeyId());
        org.junit.jupiter.api.Assertions.assertNotNull(command.getValue().discoverySecretAccessKey());
        org.junit.jupiter.api.Assertions.assertEquals("[已脱敏]", command.getValue().discoveryAccessKeyId().toString());
        org.junit.jupiter.api.Assertions.assertEquals("[已脱敏]", command.getValue().discoverySecretAccessKey().toString());
    }

    @Test
    void 平台连接列表使用平台会话和服务端分页安全摘要() throws Exception {
        var service = mock(PlatformProviderConnectionService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(context))
                .build();
        when(service.list(any())).thenReturn(PageResult.of(java.util.List.of(view()), new PageQuery(2, 20), 21));

        mvc.perform(get("/api/v1/platform/model-supply/connections")
                        .param("tenantId", "100000000000000099")
                        .param("pageNumber", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].displayName").value("平台模型"))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.items[0].endpoint").doesNotExist())
                .andExpect(jsonPath("$.items[0].credentialReference").doesNotExist());

        var query = ArgumentCaptor.forClass(ListPlatformProviderConnectionsQuery.class);
        verify(service).list(query.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, query.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(2L, query.getValue().page().pageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(20L, query.getValue().page().pageSize());
    }

    @Test
    void 平台连接探测只从平台会话取身份且不接受租户标识() throws Exception {
        var service = mock(PlatformProviderConnectionService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(context)).build();
        when(service.probe(any())).thenReturn(view());

        mvc.perform(post("/api/v1/platform/model-supply/connections/320000000000000001/probe")
                        .param("tenantId", "100000000000000099")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0,\"modelCode\":\"deterministic-embedding\",\"timeoutMillis\":3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist());
        var command = ArgumentCaptor.forClass(ProbePlatformProviderConnectionCommand.class);
        verify(service).probe(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals("320000000000000001", command.getValue().providerConnectionId());
    }

    private static PlatformProviderConnectionView view() {
        return new PlatformProviderConnectionView(
                "320000000000000001", "DETERMINISTIC", "平台模型", "本地", "API_KEY",
                true, "DRAFT", 0L, null, null, null, Instant.parse("2026-08-13T01:00:00Z"));
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
