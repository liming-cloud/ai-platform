package com.aiplatform.modelhub.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiplatform.modelhub.application.ModelConfigApplicationService;
import com.aiplatform.modelhub.application.ModelCreationApplicationService;
import com.aiplatform.modelhub.application.ModelProviderApplicationService;
import com.aiplatform.modelhub.application.error.ModelHubErrorCodes;
import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.command.DisableModelConfigCommand;
import com.aiplatform.modelhub.application.command.DisableModelProviderCommand;
import com.aiplatform.modelhub.application.command.UpdateModelConfigCommand;
import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;
import com.aiplatform.modelhub.application.result.ModelConfigDetailResult;
import com.aiplatform.modelhub.application.result.ModelConnectivityTestResult;
import com.aiplatform.modelhub.application.result.ModelProviderDetailResult;
import com.aiplatform.modelhub.presentation.converter.ModelHubPresentationConverter;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import com.aiplatform.shared.web.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelProviderControllerTest {
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        TenantContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void createProviderUsesTenantContextAndAuthenticatedPrincipal() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        ModelCreationApplicationService creationService = mock(ModelCreationApplicationService.class);
        when(creationService.createProvider(any(CreateModelProviderCommand.class)))
                .thenReturn(new CreateModelProviderResult("3001", "PENDING_APPROVAL", "REQ-3001"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        creationService,
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(post("/api/v1/model-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "provider-create-001",
                                  "providerName": "OpenAI 企业账号",
                                  "providerType": "openai",
                                  "apiEndpoint": "https://api.openai.example/v1",
                                  "authType": "api_key",
                                  "credentialMaterialInput": "sk-live-plain"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-MODEL-001"))
                .andExpect(jsonPath("$.body.providerId").value("3001"))
                .andExpect(jsonPath("$.body.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.body.approvalRequestId").value("REQ-3001"));

        ArgumentCaptor<CreateModelProviderCommand> captor = ArgumentCaptor.forClass(CreateModelProviderCommand.class);
        verify(creationService).createProvider(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().operatorId()).isEqualTo("1001");
        assertThat(captor.getValue().requestId()).isEqualTo("provider-create-001");
        assertThat(captor.getValue().providerType()).isEqualTo("openai");
        assertThat(captor.getValue().authType()).isEqualTo("api_key");
        assertThat(captor.getValue().credentialMaterialInput()).isEqualTo("sk-live-plain");
    }

    @Test
    void createProviderRejectsKnownCredentialHiddenInReviewableNameBeforeApplicationSideEffects() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelCreationApplicationService creationService = mock(ModelCreationApplicationService.class);
        String credential = "arbitrary-runtime-credential-987654";
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                credential.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelProviderController(
                        providerService,
                        mock(ModelConfigApplicationService.class),
                        creationService,
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/model-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "provider-create-secret-001",
                                  "providerName": "Provider %s",
                                  "providerType": "openai",
                                  "apiEndpoint": "https://api.openai.example/v1",
                                  "authType": "api_key",
                                  "credentialMaterialInput": "%s"
                                }
                                """.formatted(encoded, credential)))
                .andExpect(status().isBadRequest());

        verify(creationService, never()).createProvider(any());
    }

    @Test
    void createModelConfigUsesServerTenantContextAndPathProviderId() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-CONFIG-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        ModelCreationApplicationService creationService = mock(ModelCreationApplicationService.class);
        when(creationService.createModelConfig(any(CreateModelConfigCommand.class)))
                .thenReturn(new CreateModelConfigResult(
                        "3001", "4001", "gpt-4.1-mini", "CHAT", "PENDING_APPROVAL", "REQ-4001", "NOT_TESTED"
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        creationService,
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(post("/api/v1/model-providers/3001/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "model-create-001",
                                  "modelCode": "gpt-4.1-mini",
                                  "modelName": "GPT 4.1 Mini",
                                  "modelType": "CHAT",
                                  "contextWindow": 128000,
                                  "inputPricePer1k": "9999999999.99999999",
                                  "outputPricePer1k": "0.00000001",
                                  "options": {
                                    "providerKind": "openai-compatible",
                                    "temperature": 0.2,
                                    "topP": 0.8,
                                    "maxTokens": 1024
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-MODEL-CONFIG-001"))
                .andExpect(jsonPath("$.body.providerId").value("3001"))
                .andExpect(jsonPath("$.body.modelConfigId").value("4001"))
                .andExpect(jsonPath("$.body.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.body.connectivityStatus").value("NOT_TESTED"));

        ArgumentCaptor<CreateModelConfigCommand> captor = ArgumentCaptor.forClass(CreateModelConfigCommand.class);
        verify(creationService).createModelConfig(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().operatorId()).isEqualTo("1001");
        assertThat(captor.getValue().requestId()).isEqualTo("model-create-001");
        assertThat(captor.getValue().providerId()).isEqualTo("3001");
        assertThat(captor.getValue().modelType()).isEqualTo("CHAT");
        assertThat(captor.getValue().inputPricePer1k()).isEqualByComparingTo("9999999999.99999999");
        assertThat(captor.getValue().outputPricePer1k()).isEqualByComparingTo("0.00000001");
        assertThat(captor.getValue().options()).containsEntry("providerKind", "openai-compatible");
        assertThat(captor.getValue().options()).containsEntry("maxTokens", new java.math.BigDecimal("1024"));
    }

    @Test
    void createModelConfigRejectsEncodedSecretInReviewableOptionsBeforeApplicationSideEffects() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        ModelCreationApplicationService creationService = mock(ModelCreationApplicationService.class);
        String encoded = java.util.Base64.getEncoder().encodeToString(
                "api_key=super-secret-value-123456".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelProviderController(
                        mock(ModelProviderApplicationService.class),
                        configService,
                        creationService,
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/model-providers/3001/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "model-create-secret-001",
                                  "modelCode": "gpt-4.1-mini",
                                  "modelName": "GPT 4.1 Mini",
                                  "modelType": "CHAT",
                                  "contextWindow": 128000,
                                  "inputPricePer1k": 0.0004,
                                  "outputPricePer1k": 0.0016,
                                  "options": {
                                    "providerKind": "openai-compatible",
                                    "user": "%s"
                                  }
                                }
                                """.formatted(encoded)))
                .andExpect(status().isBadRequest());

        verify(creationService, never()).createModelConfig(any());
    }

    @Test
    void createModelConfigRejectsPriceThatPostgresWouldRoundBeforeApplicationSideEffects() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        ModelCreationApplicationService creationService = mock(ModelCreationApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelProviderController(
                        mock(ModelProviderApplicationService.class),
                        configService,
                        creationService,
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/model-providers/3001/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "model-create-price-001",
                                  "modelCode": "gpt-4.1-mini",
                                  "modelName": "GPT 4.1 Mini",
                                  "modelType": "CHAT",
                                  "contextWindow": 128000,
                                  "inputPricePer1k": 0.123456789,
                                  "outputPricePer1k": 0.0016,
                                  "options": {"providerKind": "openai-compatible"}
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(creationService, never()).createModelConfig(any());
    }

    @Test
    void testModelConnectivityUsesServerTenantContext() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-CONNECT-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        when(configService.testConnectivity(TenantId.of("2001"), "3001", "4001", 7))
                .thenReturn(new ModelConnectivityTestResult(
                        "3001",
                        "4001",
                        "PENDING_APPROVAL",
                        false,
                        "FAILED",
                        "模型供应商网络连接失败",
                        8
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(post("/api/v1/model-providers/3001/models/4001/connectivity-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-MODEL-CONNECT-001"))
                .andExpect(jsonPath("$.body.providerId").value("3001"))
                .andExpect(jsonPath("$.body.modelConfigId").value("4001"))
                .andExpect(jsonPath("$.body.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.body.connected").value(false))
                .andExpect(jsonPath("$.body.connectivityStatus").value("FAILED"))
                .andExpect(jsonPath("$.body.version").value(8));

        verify(configService).testConnectivity(TenantId.of("2001"), "3001", "4001", 7);
    }

    @Test
    void updateModelConfigUsesServerTenantContextAuthenticatedPrincipalAndPathIds() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-CONFIG-UPDATE-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        when(configService.updateModelConfig(any(UpdateModelConfigCommand.class)))
                .thenReturn(modelConfigDetail("PENDING_APPROVAL"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(put("/api/v1/model-providers/3001/models/4001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelCode": "gpt-4.1-mini",
                                  "modelName": "GPT 4.1 Mini 更新版",
                                  "modelType": "CHAT",
                                  "contextWindow": 64000,
                                  "inputPricePer1k": 0.0003,
                                  "outputPricePer1k": 0.0012,
                                  "expectedVersion": 7,
                                  "options": {
                                    "providerKind": "openai-compatible",
                                    "temperature": 0.4,
                                    "maxTokens": 2048
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-MODEL-CONFIG-UPDATE-001"))
                .andExpect(jsonPath("$.body.providerId").value("3001"))
                .andExpect(jsonPath("$.body.modelConfigId").value("4001"))
                .andExpect(jsonPath("$.body.modelName").value("GPT 4.1 Mini 更新版"))
                .andExpect(jsonPath("$.body.contextWindow").value(64000))
                .andExpect(jsonPath("$.body.providerKind").value("openai-compatible"))
                .andExpect(jsonPath("$.body.status").value("PENDING_APPROVAL"));

        ArgumentCaptor<UpdateModelConfigCommand> captor = ArgumentCaptor.forClass(UpdateModelConfigCommand.class);
        verify(configService).updateModelConfig(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().operatorId()).isEqualTo("1001");
        assertThat(captor.getValue().providerId()).isEqualTo("3001");
        assertThat(captor.getValue().modelConfigId()).isEqualTo("4001");
        assertThat(captor.getValue().modelName()).isEqualTo("GPT 4.1 Mini 更新版");
        assertThat(captor.getValue().expectedVersion()).isEqualTo(7);
    }

    @Test
    void updateModelConfigRejectsMissingExpectedVersionBeforeApplicationSideEffects() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelProviderController(
                        mock(ModelProviderApplicationService.class),
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(put("/api/v1/model-providers/3001/models/4001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelCode": "gpt-4.1-mini",
                                  "modelName": "GPT 4.1 Mini 更新版",
                                  "modelType": "CHAT",
                                  "contextWindow": 64000,
                                  "inputPricePer1k": 0.0003,
                                  "outputPricePer1k": 0.0012,
                                  "options": {"providerKind": "openai-compatible"}
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(configService, never()).updateModelConfig(any());
    }

    @Test
    void staleExpectedVersionUsesTheUnifiedHttpConflictContract() throws Exception {
        TraceContext.ensureTraceId("TRACE-MODEL-VERSION-CONFLICT-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        doThrow(new ApplicationException(
                ModelHubErrorCodes.VERSION_CONFLICT,
                "模型配置已被其他操作修改"
        )).when(configService).updateModelConfig(any());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelProviderController(
                        mock(ModelProviderApplicationService.class),
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(put("/api/v1/model-providers/3001/models/4001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelCode": "gpt-4.1-mini",
                                  "modelName": "GPT 4.1 Mini 更新版",
                                  "modelType": "CHAT",
                                  "contextWindow": 64000,
                                  "inputPricePer1k": 0.0003,
                                  "outputPricePer1k": 0.0012,
                                  "options": {"providerKind": "openai-compatible"},
                                  "expectedVersion": 7
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.header.code").value("AIP-MDL-VERSIONCONFLICT"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-MODEL-VERSION-CONFLICT-001"));
    }

    @Test
    void disableModelConfigUsesServerTenantContextAuthenticatedPrincipalAndPathIds() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-CONFIG-DISABLE-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        when(configService.disableModelConfig(any(DisableModelConfigCommand.class)))
                .thenReturn(modelConfigDetail("DEPRECATED"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(post("/api/v1/model-providers/3001/models/4001/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"切换到已验证的新模型\",\"expectedVersion\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-MODEL-CONFIG-DISABLE-001"))
                .andExpect(jsonPath("$.body.providerId").value("3001"))
                .andExpect(jsonPath("$.body.modelConfigId").value("4001"))
                .andExpect(jsonPath("$.body.status").value("DEPRECATED"));

        ArgumentCaptor<DisableModelConfigCommand> captor = ArgumentCaptor.forClass(DisableModelConfigCommand.class);
        verify(configService).disableModelConfig(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().operatorId()).isEqualTo("1001");
        assertThat(captor.getValue().providerId()).isEqualTo("3001");
        assertThat(captor.getValue().modelConfigId()).isEqualTo("4001");
        assertThat(captor.getValue().reason()).isEqualTo("切换到已验证的新模型");
        assertThat(captor.getValue().expectedVersion()).isEqualTo(7);
    }

    @Test
    void disableProviderUsesServerTenantContextAuthenticatedPrincipalAndPathProviderId() throws Exception {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-DISABLE-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        when(providerService.disableProvider(any(DisableModelProviderCommand.class)))
                .thenReturn(new ModelProviderDetailResult(
                        "3001",
                        "openai",
                        "OpenAI 企业账号",
                        "https://api.openai.example/v1",
                        "DISABLED",
                        "REQ-3001",
                        8
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(post("/api/v1/model-providers/3001/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"供应商合同已到期\",\"expectedVersion\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.code").value("SUCCESS"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FILTER-MODEL-DISABLE-001"))
                .andExpect(jsonPath("$.body.providerId").value("3001"))
                .andExpect(jsonPath("$.body.status").value("DISABLED"));

        ArgumentCaptor<DisableModelProviderCommand> captor = ArgumentCaptor.forClass(DisableModelProviderCommand.class);
        verify(providerService).disableProvider(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().operatorId()).isEqualTo("1001");
        assertThat(captor.getValue().providerId()).isEqualTo("3001");
        assertThat(captor.getValue().reason()).isEqualTo("供应商合同已到期");
        assertThat(captor.getValue().expectedVersion()).isEqualTo(7);
    }

    @Test
    void disableProviderRejectsMissingReasonBeforeCallingApplicationService() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ModelProviderController(
                        providerService,
                        mock(ModelConfigApplicationService.class),
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/model-providers/3001/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \",\"expectedVersion\":7}"))
                .andExpect(status().isBadRequest());

        verify(providerService, never()).disableProvider(any());
    }

    @Test
    void embeddingOptionsExposeOnlyActiveEmbeddingModelsFromActiveProviders() throws Exception {
        TenantContext.set(TenantId.of("2001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        when(providerService.listProviders(TenantId.of("2001"))).thenReturn(List.of(
                providerDetail("3001", "ACTIVE"),
                providerDetail("3002", "PENDING_APPROVAL"),
                providerDetail("3003", "DISABLED")
        ));
        when(configService.listModelConfigs(TenantId.of("2001"), "3001")).thenReturn(List.of(
                embeddingModel("3001", "4101", "ACTIVE"),
                embeddingModel("3001", "4102", "PENDING_APPROVAL"),
                embeddingModel("3001", "4103", "INACTIVE"),
                embeddingModel("3001", "4104", "REJECTED"),
                embeddingModel("3001", "4105", "DEPRECATED"),
                modelConfigDetail("ACTIVE")
        ));
        when(configService.listModelConfigs(TenantId.of("2001"), "3002"))
                .thenReturn(List.of(embeddingModel("3002", "4201", "ACTIVE")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ModelProviderController(
                        providerService,
                        configService,
                        mock(ModelCreationApplicationService.class),
                        new ModelHubPresentationConverter(),
                        allowAllFeatures()
                )
        ).build();

        mockMvc.perform(get("/api/v1/model-providers/embedding-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.length()").value(1))
                .andExpect(jsonPath("$.body[0].modelConfigId").value("4101"));

        verify(configService, never()).listModelConfigs(TenantId.of("2001"), "3002");
        verify(configService, never()).listModelConfigs(TenantId.of("2001"), "3003");
    }

    @Test
    void listProvidersFailsClosedWhenTenantFeatureIsDisabled() throws Exception {
        TraceContext.ensureTraceId("TRACE-FEATURE-DISABLED-001");
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));
        ModelProviderApplicationService providerService = mock(ModelProviderApplicationService.class);
        ModelConfigApplicationService configService = mock(ModelConfigApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new ModelProviderController(
                                providerService,
                                configService,
                                mock(ModelCreationApplicationService.class),
                                new ModelHubPresentationConverter(),
                                new TenantFeatureGuard((tenantId, featureCode) -> false)
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/model-providers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.header.code").value("AIP-FEATURE-PERMISSIONDENIED"))
                .andExpect(jsonPath("$.header.traceId").value("TRACE-FEATURE-DISABLED-001"));

        verify(providerService, never()).listProviders(any());
    }

    private static TenantFeatureGuard allowAllFeatures() {
        return new TenantFeatureGuard((tenantId, featureCode) -> true);
    }

    private static ModelConfigDetailResult modelConfigDetail(String status) {
        return new ModelConfigDetailResult(
                "3001",
                "4001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini 更新版",
                "CHAT",
                64000,
                null,
                "0.0003",
                "0.0012",
                "openai-compatible",
                "0.4",
                null,
                2048,
                status,
                "PENDING_APPROVAL".equals(status) ? "REQ-4001" : null,
                "NOT_TESTED",
                "",
                7
        );
    }

    private static ModelProviderDetailResult providerDetail(String providerId, String status) {
        return new ModelProviderDetailResult(
                providerId,
                "openai-compatible",
                "Provider " + providerId,
                "https://api.example.invalid/v1",
                status,
                "PENDING_APPROVAL".equals(status) ? "REQ-" + providerId : null,
                7
        );
    }

    private static ModelConfigDetailResult embeddingModel(
            String providerId,
            String modelConfigId,
            String status
    ) {
        return new ModelConfigDetailResult(
                providerId,
                modelConfigId,
                "embedding-" + modelConfigId,
                "Embedding " + modelConfigId,
                "EMBEDDING",
                8192,
                1536,
                "0.0001",
                "0",
                "openai-compatible",
                null,
                null,
                null,
                status,
                "PENDING_APPROVAL".equals(status) ? "REQ-" + modelConfigId : null,
                "PASSED",
                "连通性通过",
                7
        );
    }
}
