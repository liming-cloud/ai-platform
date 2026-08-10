package com.aiplatform.foundation.modelruntime.interfaces.web.provider.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.foundation.modelruntime.application.provider.command.DiscoverProviderModelsCommand;
import com.aiplatform.foundation.modelruntime.application.provider.command.CreateProviderConnectionCommand;
import com.aiplatform.foundation.modelruntime.application.provider.command.RotateProviderConnectionCredentialCommand;
import com.aiplatform.foundation.modelruntime.application.provider.dto.CreateProviderConnectionResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.RotateProviderConnectionCredentialResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderConnectionView;
import com.aiplatform.foundation.modelruntime.application.provider.dto.DiscoverProviderModelsResult;
import com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryView;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderConnectionService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryReadService;
import com.aiplatform.foundation.modelruntime.application.provider.service.ProviderModelDiscoveryService;
import com.aiplatform.foundation.modelruntime.application.provider.query.ListProviderModelDiscoveriesQuery;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
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
    void 创建连接HTTP只写入APIKey且不回显密钥引用() throws Exception {
        var connections = mock(ProviderConnectionService.class);
        var discoveries = mock(ProviderModelDiscoveryService.class);
        var history = mock(ProviderModelDiscoveryReadService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ProviderConnectionController(connections, discoveries, history);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(connections.create(any())).thenReturn(new CreateProviderConnectionResult(
                new ProviderConnectionView(
                        "310000000000000001", "DETERMINISTIC", "本地模型", "本地", "API_KEY",
                        true, "DRAFT", 0, null, null, null, Instant.parse("2026-08-10T04:00:00Z")),
                false));

        mvc.perform(post("/api/v1/model-providers")
                        .header("Idempotency-Key", "provider-request-0001")
                        .contentType("application/json")
                        .content("{" +
                                "\"providerKind\":\"DETERMINISTIC\",\"displayName\":\"本地模型\","
                                + "\"endpoint\":\"memory://deterministic\",\"region\":\"本地\","
                                + "\"authenticationType\":\"API_KEY\",\"apiKey\":\"test-api-key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.credentialReference").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        var command = ArgumentCaptor.forClass(CreateProviderConnectionCommand.class);
        verify(connections).create(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals("[已脱敏]", command.getValue().apiKey().toString());
    }

    @Test
    void 轮换凭据HTTP只接收一次性APIKey并使连接回到待验证状态() throws Exception {
        var connections = mock(ProviderConnectionService.class);
        var discoveries = mock(ProviderModelDiscoveryService.class);
        var history = mock(ProviderModelDiscoveryReadService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ProviderConnectionController(connections, discoveries, history);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(connections.rotateCredential(any())).thenReturn(new RotateProviderConnectionCredentialResult(
                new ProviderConnectionView(
                        "310000000000000001", "DETERMINISTIC", "本地模型", "本地", "API_KEY",
                        true, "DRAFT", 2, null, null, null, Instant.parse("2026-08-10T04:10:00Z"))));

        mvc.perform(post("/api/v1/model-providers/310000000000000001/credential-rotation")
                        .param("tenantId", "100000000000000099")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1,\"apiKey\":\"next-api-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.credentialReference").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        var command = ArgumentCaptor.forClass(RotateProviderConnectionCredentialCommand.class);
        verify(connections).rotateCredential(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals("310000000000000001", command.getValue().providerConnectionId());
        org.junit.jupiter.api.Assertions.assertEquals(1, command.getValue().expectedVersion());
        org.junit.jupiter.api.Assertions.assertEquals("[已脱敏]", command.getValue().apiKey().toString());
    }

    @Test
    void 模型发现HTTP入口只从可信上下文取身份且不回显连接地址() throws Exception {
        var connections = mock(ProviderConnectionService.class);
        var discoveries = mock(ProviderModelDiscoveryService.class);
        var history = mock(ProviderModelDiscoveryReadService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ProviderConnectionController(connections, discoveries, history);
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

    @Test
    void 发现历史HTTP入口使用可信上下文和服务端分页安全摘要() throws Exception {
        var connections = mock(ProviderConnectionService.class);
        var discoveries = mock(ProviderModelDiscoveryService.class);
        var history = mock(ProviderModelDiscoveryReadService.class);
        var context = mock(TrustedRequestContext.class);
        var controller = new ProviderConnectionController(connections, discoveries, history);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(trustedContext(context))
                .build();
        when(history.list(any())).thenReturn(PageResult.of(
                java.util.List.of(new ProviderModelDiscoveryView(
                        "340000000000000001", 1, true, 2,
                        "DETERMINISTIC_DISCOVERY_OK", Duration.ofMillis(10),
                        Instant.parse("2026-08-10T03:00:00Z"),
                        Instant.parse("2026-08-11T03:00:00Z"), "CREATE_DEPLOYMENT")),
                new PageQuery(2, 20), 21));

        mvc.perform(get("/api/v1/model-providers/310000000000000001/model-discoveries")
                        .param("tenantId", "100000000000000099")
                        .param("pageNumber", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].candidateCount").value(2))
                .andExpect(jsonPath("$.items[0].nextStep").value("CREATE_DEPLOYMENT"))
                .andExpect(jsonPath("$.items[0].models").doesNotExist())
                .andExpect(jsonPath("$.items[0].endpoint").doesNotExist())
                .andExpect(jsonPath("$.items[0].credentialReference").doesNotExist());

        var query = ArgumentCaptor.forClass(ListProviderModelDiscoveriesQuery.class);
        verify(history).list(query.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, query.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(
                "310000000000000001", query.getValue().providerConnectionId());
        org.junit.jupiter.api.Assertions.assertEquals(2, query.getValue().page().pageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(20, query.getValue().page().pageSize());
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
