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
import com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProbeProviderService;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialWriter;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import com.aiplatform.foundation.modelruntime.domain.provider.model.AuthenticationType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
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
    void 创建平台连接缺少预设来源时不能触及服务() throws Exception {
        var service = mock(PlatformProviderConnectionService.class);
        when(service.create(any())).thenReturn(new CreatePlatformProviderConnectionResult(view(), false));
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(mock(PlatformRequestContext.class))).build();
        var failure = org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(post("/api/v1/platform/model-supply/connections")
                        .header("Idempotency-Key", "platform-preset-required-0001")
                        .contentType("application/json")
                        .content("""
                                {"providerKind":"OPENAI_COMPATIBLE","discoveryProfile":"OPENAI_MODELS_API",
                                 "displayName":"缺少预设的连接","endpoint":"https://models.example.invalid/v1",
                                 "region":"unspecified","authenticationType":"API_KEY","apiKey":"synthetic-test-key"}
                                """)));
        org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void 平台连接预设提供原型五厂商和显式能力而不访问凭据() throws Exception {
        var repository = mock(PlatformProviderConnectionRepository.class);
        var credentials = mock(PlatformProviderConnectionCredentialWriter.class);
        var transactions = mock(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction.class);
        var identifiers = mock(com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator.class);
        var probes = mock(PlatformProbeProviderService.class);
        var service = new PlatformProviderConnectionService(repository, credentials, transactions,
                probes, java.time.Clock.systemUTC(), identifiers, endpoint -> {});
        var context = mock(PlatformRequestContext.class);
        when(context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(context)).build();

        mvc.perform(get("/api/v1/platform/model-supply/connections/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].id").value("CUSTOM"))
                .andExpect(jsonPath("$.items[1].id").value("DEEPSEEK"))
                .andExpect(jsonPath("$.items[2].id").value("ALIYUN"))
                .andExpect(jsonPath("$.items[3].id").value("SILICONFLOW"))
                .andExpect(jsonPath("$.items[4].id").value("VOLCENGINE"))
                .andExpect(jsonPath("$.items[4].capabilities[0]").value("CHAT"))
                .andExpect(jsonPath("$.items[4].discoveryProfile").value("VOLCENGINE_ARK_CONTROL_PLANE"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
        org.mockito.Mockito.verifyNoInteractions(repository, credentials, transactions, identifiers, probes);
    }

    @Test
    void 平台连接详情按不透明标识只读恢复安全摘要() throws Exception {
        var repository = mock(PlatformProviderConnectionRepository.class);
        var credentials = mock(PlatformProviderConnectionCredentialWriter.class);
        var transactions = mock(com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderConnectionCredentialTransaction.class);
        var identifiers = mock(com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator.class);
        var probes = mock(PlatformProbeProviderService.class);
        var service = new PlatformProviderConnectionService(repository, credentials, transactions,
                probes, java.time.Clock.systemUTC(), identifiers, endpoint -> {});
        var context = mock(PlatformRequestContext.class);
        when(context.hasPermission(PlatformPermissions.MODEL_PLATFORM_WRITE)).thenReturn(true);
        var id = new PlatformProviderConnectionId(320000000000000001L);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(PlatformProviderConnection.create(
                id,
                ProviderKind.OPENAI_COMPATIBLE,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection("CUSTOM", "2026-10-12", "CHAT", null),
                "平台模型",
                java.net.URI.create("https://models.example.invalid/v1"),
                "cn-beijing",
                AuthenticationType.API_KEY,
                new CredentialReference("secret://platform-provider-connection/320000000000000001"),
                Instant.parse("2026-10-12T00:00:00Z"))));
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(context)).build();

        mvc.perform(get("/api/v1/platform/model-supply/connections/{id}", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.displayName").value("平台模型"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.discoveryAccessKeyId").doesNotExist())
                .andExpect(jsonPath("$.discoverySecretAccessKey").doesNotExist());
        verify(repository).findById(id);
        org.mockito.Mockito.verifyNoMoreInteractions(repository);
        org.mockito.Mockito.verifyNoInteractions(credentials, transactions, identifiers, probes);
    }

    @Test
    void 平台连接详情拒绝外部租户与未知参数且不调用服务() throws Exception {
        var service = mock(PlatformProviderConnectionService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(service))
                .setCustomArgumentResolvers(platformContext(mock(PlatformRequestContext.class))).build();
        for (var field : java.util.List.of("tenantId", "subjectId", "unknown")) {
            // 此模块不依赖平台 Web；验证真实控制器拒绝，HTTP 错误映射由平台既有合同负责。
            var failure = org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class,
                    () -> mvc.perform(get("/api/v1/platform/model-supply/connections/320000000000000001").param(field, "100")));
            org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        }
        org.mockito.Mockito.verifyNoInteractions(service);
    }

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
                                "\"providerKind\":\"OPENAI_COMPATIBLE\",\"displayName\":\"平台模型\","
                                + "\"providerPresetId\":\"CUSTOM\",\"providerPresetVersion\":\"2026-10-12\",\"capability\":\"CHAT\","
                                + "\"discoveryProfile\":\"OPENAI_MODELS_API\","
                                + "\"endpoint\":\"https://models.example.invalid/v1\",\"region\":\"unspecified\","
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
                        .OPENAI_MODELS_API,
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
                                "\"providerPresetId\":\"VOLCENGINE\",\"providerPresetVersion\":\"2026-10-12\",\"capability\":\"CHAT\","+
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

    @Test
    void 平台连接拥有独立发布入口而不是用已验证状态代替发布() throws Exception {
        var connections = mock(PlatformProviderConnectionService.class);
        var publications = mock(com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionPublicationService.class);
        var context = mock(PlatformRequestContext.class);
        when(publications.publish(any())).thenReturn(new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderConnectionPublicationView(
                "320000000000000001", true, 1L, 1L, Instant.parse("2026-10-12T00:00:00Z"), Instant.parse("2026-10-12T00:00:00Z")));
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionController(connections),
                        new PlatformProviderConnectionPublicationController(publications))
                .setCustomArgumentResolvers(platformContext(context)).build();
        mvc.perform(post("/api/v1/platform/model-supply/connections/320000000000000001/publish")
                        .param("tenantId", "100000000000000099")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.connectionRevision").value(1))
                .andExpect(jsonPath("$.publicationRevision").value(1))
                .andExpect(jsonPath("$.endpoint").doesNotExist())
                .andExpect(jsonPath("$.credentialReference").doesNotExist())
                .andExpect(jsonPath("$.publishedBySubjectId").doesNotExist());
        var command = ArgumentCaptor.forClass(com.aiplatform.foundation.modelruntime.application.platformsupply.command.PublishPlatformProviderConnectionCommand.class);
        verify(publications).publish(command.capture());
        org.junit.jupiter.api.Assertions.assertSame(context, command.getValue().context());
        org.junit.jupiter.api.Assertions.assertEquals(1L, command.getValue().expectedVersion());
    }

    @Test
    void 连接发布不能把缺失预期版本当作零版本() throws Exception {
        var publications = mock(com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformProviderConnectionPublicationService.class);
        var context = mock(PlatformRequestContext.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PlatformProviderConnectionPublicationController(publications))
                .setCustomArgumentResolvers(platformContext(context)).build();
        mvc.perform(post("/api/v1/platform/model-supply/connections/320000000000000001/publish")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(publications);
    }

    private static PlatformProviderConnectionView view() {
        return new PlatformProviderConnectionView(
                "320000000000000001",
                "DETERMINISTIC",
                "平台模型",
                "DETERMINISTIC_CATALOG",
                "本地",
                "API_KEY",
                true,
                "DRAFT",
                0L,
                null,
                null,
                null,
                Instant.parse("2026-08-13T01:00:00Z"),
                null);
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
