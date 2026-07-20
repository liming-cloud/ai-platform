package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.application.command.CreateModelConfigCommand;
import com.aiplatform.modelhub.application.command.DisableModelConfigCommand;
import com.aiplatform.modelhub.application.command.UpdateModelConfigCommand;
import com.aiplatform.modelhub.application.query.ModelConfigQueryRepository;
import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.application.result.ModelConfigDetailResult;
import com.aiplatform.modelhub.application.result.ModelConnectivityTestResult;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.ModelConfigCreationService;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelConfigApplicationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-25T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void createModelConfigValidatesRuntimeOptionsPersistsConfigAndRequestsApproval() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findByProviderAndCode(TenantId.of("2001"), "3001", "gpt-4.1-mini")).thenReturn(Optional.empty());
        when(configs.nextModelConfigId()).thenReturn("4001");
        when(runtimeOptions.validate(any(ModelProvider.class), any(ModelConfig.class)))
                .thenReturn(ModelRuntimeValidation.passed());
        when(configs.insert(any(ModelConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalPort.requestApproval(any(ModelConfigChange.class)))
                .thenAnswer(invocation -> invocation.<ModelConfigChange>getArgument(0).changeId());
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        CreateModelConfigResult result = service.createModelConfig(new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-request-test",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                Map.of(
                        "providerKind", "openai-compatible",
                        "temperature", 0.2,
                        "maxTokens", 1024,
                        "user", "runtime-private-marker-987654"
                )
        ));

        assertThat(result.modelConfigId()).isEqualTo("4001");
        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.connectivityStatus()).isEqualTo("NOT_TESTED");
        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).insert(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(captor.getValue().modelType()).isEqualTo(ModelType.CHAT);
        assertThat(captor.getValue().options().values()).containsEntry("maxTokens", new BigDecimal("1024"));
        ArgumentCaptor<ModelConfig> validated = ArgumentCaptor.forClass(ModelConfig.class);
        verify(runtimeOptions).validate(org.mockito.ArgumentMatchers.eq(provider()), validated.capture());
        assertThat(validated.getValue().approvalEvidenceDigest()).isNull();
        assertThat(validated.getValue().modelConfigId()).isEqualTo(captor.getValue().modelConfigId());
        verify(usagePolicyPublisher).publish(provider(), captor.getValue());
        ArgumentCaptor<ModelConfigChange> changeCaptor = ArgumentCaptor.forClass(ModelConfigChange.class);
        verify(approvalPort).requestApproval(changeCaptor.capture());
        assertThat(changeCaptor.getValue().approvalType()).isEqualTo("model_config_change");
        assertThat(changeCaptor.getValue().sourceResourceType()).isEqualTo("model_config");
        assertThat(changeCaptor.getValue().sourceResourceId()).isEqualTo("4001");
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        AuditEvent audit = auditCaptor.getValue();
        assertThat(audit.tenantId()).isEqualTo("2001");
        assertThat(audit.actorId()).isEqualTo("1001");
        assertThat(audit.resourceType()).isEqualTo("MODEL_CONFIG");
        assertThat(audit.resourceId()).isEqualTo("4001");
        assertThat(audit.action()).isEqualTo("MODEL_CONFIG_CREATE");
        assertThat(audit.riskLevel()).isEqualTo("HIGH");
        assertThat(audit.approvalReferenceId()).isEqualTo(result.approvalRequestId());
        assertThat(audit.detailAttributes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "providerId", "3001",
                "modelCode", "gpt-4.1-mini",
                "modelName", "GPT 4.1 Mini",
                "modelType", "CHAT",
                "status", "PENDING_APPROVAL",
                "connectivityStatus", "NOT_TESTED"
        ));
        assertThat(audit.detailAttributes().toString())
                .doesNotContain("options", "temperature", "maxTokens", "runtime-private-marker-987654", "sk-***cret");
    }

    @Test
    void createModelConfigBlocksDuplicateModelCodeUnderSameProvider() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findByProviderAndCode(TenantId.of("2001"), "3001", "gpt-4.1-mini"))
                .thenReturn(Optional.of(config()));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createModelConfig(command()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("模型配置已存在");

        verify(runtimeOptions, never()).validate(any(), any());
        verify(configs, never()).insert(any());
        verifyNoInteractions(approvalPort);
        verify(usagePolicyPublisher, never()).publish(any(), any());
    }

    @Test
    void createModelConfigBlocksInvalidRuntimeOptions() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findByProviderAndCode(TenantId.of("2001"), "3001", "gpt-4.1-mini")).thenReturn(Optional.empty());
        when(configs.nextModelConfigId()).thenReturn("4001");
        when(runtimeOptions.validate(any(ModelProvider.class), any(ModelConfig.class)))
                .thenReturn(ModelRuntimeValidation.failed(java.util.List.of("temperature 必须在 0 到 2 之间")));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createModelConfig(command()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("options 校验失败");

        verify(configs, never()).insert(any());
        verifyNoInteractions(approvalPort);
        verify(usagePolicyPublisher, never()).publish(any(), any());
    }

    @Test
    void createModelConfigRejectsMismatchedProviderKindBeforeIdAllocationOrSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createModelConfig(command(Map.of(
                "providerKind", "bedrock",
                "temperature", 0.2
        ))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("providerKind")
                .hasMessageContaining("openai-compatible");

        verify(configs, never()).findByProviderAndCode(any(), any(), any());
        verify(configs, never()).nextModelConfigId();
        verify(configs, never()).insert(any());
        verifyNoInteractions(runtimeOptions, approvalPort, usagePolicyPublisher, auditPublisher);
    }

    @Test
    void createModelConfigRejectsMissingContextWindowBeforeRuntimeValidation() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createModelConfig(new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-request-test",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                null,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                Map.of("providerKind", "openai-compatible")
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上下文窗口不能为空");

        verify(providers, never()).findById(any(), any());
        verify(runtimeOptions, never()).validate(any(), any());
        verify(configs, never()).insert(any());
        verifyNoInteractions(approvalPort);
        verify(usagePolicyPublisher, never()).publish(any(), any());
    }

    @Test
    void createModelConfigCommandRejectsMissingOptionsInsteadOfEmptyRuntimeOptions() {
        assertThatThrownBy(() -> new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-request-test",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时 options不能为空");
    }

    @Test
    void createModelConfigCommandRejectsDamagedOptionsInsteadOfJdkNullPointerException() {
        Map<String, Object> optionsWithNullValue = new LinkedHashMap<>();
        optionsWithNullValue.put("providerKind", "openai-compatible");
        optionsWithNullValue.put("temperature", null);
        Map<String, Object> optionsWithNullKey = new LinkedHashMap<>();
        optionsWithNullKey.put(null, "openai-compatible");

        assertThatThrownBy(() -> command(optionsWithNullValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时 options value不能为空");
        assertThatThrownBy(() -> command(optionsWithNullKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时 options key不能为空");
    }

    @Test
    void createModelConfigCommandRejectsMissingPriceInsteadOfZeroFallback() {
        assertThatThrownBy(() -> new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-request-test",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                null,
                new BigDecimal("0.0016"),
                Map.of("providerKind", "openai-compatible")
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("输入单价不能为空");
    }

    @Test
    void normalizesCreateModelConfigCommandFactsBeforeServiceUse() {
        CreateModelConfigCommand command = new CreateModelConfigCommand(
                TenantId.of("2001"),
                " 1001 ",
                "model-request-test",
                " 3001 ",
                " gpt-4.1-mini ",
                " GPT 4.1 Mini ",
                " CHAT ",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                Map.of("providerKind", "openai-compatible")
        );

        assertThat(command.operatorId()).isEqualTo("1001");
        assertThat(command.providerId()).isEqualTo("3001");
        assertThat(command.modelCode()).isEqualTo("gpt-4.1-mini");
        assertThat(command.modelName()).isEqualTo("GPT 4.1 Mini");
        assertThat(command.modelType()).isEqualTo("CHAT");
    }

    @Test
    void canonicalizesBedrockEmbeddingOptionsBeforePersistenceAndApproval() {
        CreateModelConfigCommand command = new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-request-test",
                "3001",
                "cohere.embed-multilingual-v3",
                "Cohere multilingual",
                "EMBEDDING",
                8192,
                1024,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Map.of(
                        "providerKind", " bedrock ",
                        "region", " us-east-1 ",
                        "timeoutSeconds", 15,
                        "embeddingProvider", " cohere ",
                        "inputType", " SEARCH_DOCUMENT ",
                        "truncate", " END "
                )
        );

        assertThat(command.options()).containsEntry("providerKind", "bedrock")
                .containsEntry("region", "us-east-1")
                .containsEntry("embeddingProvider", "cohere")
                .containsEntry("inputType", "SEARCH_DOCUMENT")
                .containsEntry("truncate", "END")
                .containsEntry("timeoutSeconds", new BigDecimal("15"));
    }

    @Test
    void testConnectivityPersistsLatestConnectivityStatus() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(config()));
        when(runtimeOptions.testConnectivity(provider(), config()))
                .thenReturn(new ModelRuntimeConnectivity(false, ModelConnectivityStatus.FAILED, "模型供应商网络连接失败"));
        when(configs.compareAndSet(any(ModelConfig.class), eq(0), eq(ModelConfigStatus.ACTIVE)))
                .thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        ModelConnectivityTestResult result = service.testConnectivity(TenantId.of("2001"), "3001", "4001", 0);

        assertThat(result.connected()).isFalse();
        assertThat(result.status()).isEqualTo("INACTIVE");
        assertThat(result.connectivityStatus()).isEqualTo("FAILED");
        ArgumentCaptor<ModelConfig> captor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSet(captor.capture(), eq(0), eq(ModelConfigStatus.ACTIVE));
        assertThat(captor.getValue().status()).isEqualTo(ModelConfigStatus.INACTIVE);
        assertThat(captor.getValue().connectivityCheckedAt()).isEqualTo(Instant.parse("2026-05-25T03:00:00Z"));
        assertThat(captor.getValue().connectivityMessage()).isEqualTo("模型供应商网络连接失败");
        verifyNoInteractions(approvalPort);
        verify(usagePolicyPublisher).publish(provider(), captor.getValue());
    }

    @Test
    void testConnectivityRejectsPendingApprovalBeforeRuntimeOrMutationSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelConfig pendingConfig = config(ModelConfigStatus.PENDING_APPROVAL, ModelConnectivityStatus.NOT_TESTED);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(pendingConfig));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                mock(ModelConfigApprovalPort.class),
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.testConnectivity(TenantId.of("2001"), "3001", "4001", 0))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("审批通过后");

        verifyNoInteractions(runtimeOptions, usagePolicyPublisher);
        verify(configs, never()).compareAndSet(any(), anyInt(), any());
    }

    @Test
    void testConnectivityRejectsProviderAwaitingApprovalBeforeExternalRuntimeCall() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelConfig inactiveConfig = config(ModelConfigStatus.INACTIVE, ModelConnectivityStatus.NOT_TESTED);
        when(providers.findById(TenantId.of("2001"), "3001"))
                .thenReturn(Optional.of(provider(ModelProviderStatus.PENDING_APPROVAL)));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(inactiveConfig));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                mock(ModelConfigApprovalPort.class),
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.testConnectivity(TenantId.of("2001"), "3001", "4001", 0))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("供应商审批通过");

        verifyNoInteractions(runtimeOptions, usagePolicyPublisher);
        verify(configs, never()).compareAndSet(any(), anyInt(), any());
    }

    @Test
    void testConnectivityActivatesOnlyAnApprovedInactiveModelAfterPassedProbe() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelConfig inactiveConfig = config(ModelConfigStatus.INACTIVE, ModelConnectivityStatus.NOT_TESTED);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(inactiveConfig));
        when(runtimeOptions.testConnectivity(provider(), inactiveConfig))
                .thenReturn(new ModelRuntimeConnectivity(true, ModelConnectivityStatus.PASSED, "模型连接测试通过"));
        when(configs.compareAndSet(any(ModelConfig.class), eq(0), eq(ModelConfigStatus.INACTIVE)))
                .thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                mock(ModelConfigApprovalPort.class),
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        ModelConnectivityTestResult result = service.testConnectivity(TenantId.of("2001"), "3001", "4001", 0);

        assertThat(result.connected()).isTrue();
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.connectivityStatus()).isEqualTo("PASSED");
        ArgumentCaptor<ModelConfig> saved = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSet(saved.capture(), eq(0), eq(ModelConfigStatus.INACTIVE));
        assertThat(saved.getValue().status()).isEqualTo(ModelConfigStatus.ACTIVE);
        verify(usagePolicyPublisher).publish(provider(), saved.getValue());
    }

    @Test
    void createModelConfigRejectsProviderAwaitingApprovalBeforeIdAllocationOrSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001"))
                .thenReturn(Optional.of(provider(ModelProviderStatus.PENDING_APPROVAL)));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createModelConfig(command()))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("供应商审批通过后");

        verify(configs, never()).findByProviderAndCode(any(), any(), any());
        verify(configs, never()).nextModelConfigId();
        verifyNoInteractions(runtimeOptions, approvalPort, usagePolicyPublisher);
    }

    @Test
    void createModelConfigRejectsHistoricalLocalProviderWhenCapabilityIsDisabledBeforeIdOrSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(localProvider()));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK,
                false
        );

        assertThatThrownBy(() -> service.createModelConfig(command(Map.of(
                "providerKind", "local-deterministic",
                "temperature", 0
        ))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("本地确定性运行时能力未启用");

        verifyNoInteractions(configs, runtimeOptions, approvalPort, usagePolicyPublisher, auditPublisher);
    }

    @Test
    void updateModelConfigRejectsHistoricalLocalProviderWhenCapabilityIsDisabledBeforeModelReadOrSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(localProvider()));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK,
                false
        );

        assertThatThrownBy(() -> service.updateModelConfig(updateCommand(Map.of(
                "providerKind", "local-deterministic",
                "temperature", 0
        ))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("本地确定性运行时能力未启用");

        verifyNoInteractions(configs, runtimeOptions, approvalPort, usagePolicyPublisher, auditPublisher);
    }

    @Test
    void testConnectivityRejectsHistoricalLocalProviderWhenCapabilityIsDisabledBeforeModelReadOrRuntimeCall() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(localProvider()));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK,
                false
        );

        assertThatThrownBy(() -> service.testConnectivity(TenantId.of("2001"), "3001", "4001", 0))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("本地确定性运行时能力未启用");

        verifyNoInteractions(configs, runtimeOptions, approvalPort, usagePolicyPublisher, auditPublisher);
    }

    @Test
    void updateModelConfigResetsConnectivityRequestsApprovalAndPublishesAuditEvent() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(config()));
        when(configs.findByProviderAndCode(TenantId.of("2001"), "3001", "gpt-4.1-mini"))
                .thenReturn(Optional.empty());
        when(runtimeOptions.validate(any(ModelProvider.class), any(ModelConfig.class)))
                .thenReturn(ModelRuntimeValidation.passed());
        when(configs.compareAndSet(any(ModelConfig.class), eq(0), eq(ModelConfigStatus.ACTIVE)))
                .thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        when(approvalPort.requestApproval(any(ModelConfigChange.class)))
                .thenAnswer(invocation -> invocation.<ModelConfigChange>getArgument(0).changeId());
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        ModelConfigDetailResult result = service.updateModelConfig(new UpdateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "3001",
                "4001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini 更新版",
                "CHAT",
                64000,
                null,
                new BigDecimal("0.0003"),
                new BigDecimal("0.0012"),
                Map.of("providerKind", "openai-compatible", "temperature", 0.4, "maxTokens", 2048),
                0
        ));

        assertThat(result.modelConfigId()).isEqualTo("4001");
        assertThat(result.modelName()).isEqualTo("GPT 4.1 Mini 更新版");
        assertThat(result.contextWindow()).isEqualTo(64000);
        assertThat(result.inputPricePer1k()).isEqualTo("0.0003");
        assertThat(result.providerKind()).isEqualTo("openai-compatible");
        assertThat(result.temperature()).isEqualTo("0.4");
        assertThat(result.maxTokens()).isEqualTo(2048);
        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.connectivityStatus()).isEqualTo("NOT_TESTED");

        ArgumentCaptor<ModelConfig> configCaptor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSet(configCaptor.capture(), eq(0), eq(ModelConfigStatus.ACTIVE));
        assertThat(configCaptor.getValue().connectivityCheckedAt()).isNull();
        assertThat(configCaptor.getValue().connectivityMessage()).isEmpty();
        assertThat(configCaptor.getValue().options().values()).containsEntry("maxTokens", new BigDecimal("2048"));
        ArgumentCaptor<ModelConfig> validated = ArgumentCaptor.forClass(ModelConfig.class);
        verify(runtimeOptions).validate(org.mockito.ArgumentMatchers.eq(provider()), validated.capture());
        assertThat(validated.getValue().approvalEvidenceDigest()).isNull();
        assertThat(validated.getValue().modelConfigId()).isEqualTo(configCaptor.getValue().modelConfigId());
        verify(usagePolicyPublisher).publish(provider(), configCaptor.getValue());
        ArgumentCaptor<ModelConfigChange> changeCaptor = ArgumentCaptor.forClass(ModelConfigChange.class);
        verify(approvalPort).requestApproval(changeCaptor.capture());
        assertThat(changeCaptor.getValue().changeSummary()).contains("更新模型配置");

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().resourceType()).isEqualTo("MODEL_CONFIG");
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("4001");
        assertThat(auditCaptor.getValue().action()).isEqualTo("MODEL_CONFIG_UPDATE");
        assertThat(auditCaptor.getValue().approvalReferenceId()).isEqualTo(changeCaptor.getValue().changeId());
        assertThat(auditCaptor.getValue().riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void updateModelConfigRejectsMismatchedProviderKindBeforeMutationSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(config()));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.updateModelConfig(new UpdateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "3001",
                "4001",
                "gpt-4.1-mini",
                "错误运行时类型",
                "CHAT",
                64000,
                null,
                new BigDecimal("0.0003"),
                new BigDecimal("0.0012"),
                Map.of("providerKind", "future-provider", "temperature", 0.4),
                0
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("暂不支持模型供应商类型")
                .hasMessageContaining("future-provider");

        verify(configs, never()).findByProviderAndCode(any(), any(), any());
        verify(configs, never()).compareAndSet(any(), anyInt(), any());
        verifyNoInteractions(runtimeOptions, approvalPort, usagePolicyPublisher, auditPublisher);
    }

    @Test
    void updateModelConfigRejectsPendingApprovalBeforeRuntimeValidationOrMutationSideEffects() {
        assertUpdateRejectedBeforeMutation(ModelConfigStatus.PENDING_APPROVAL, "待审批");
    }

    @Test
    void updateModelConfigRejectsDeprecatedConfigBeforeRuntimeValidationOrMutationSideEffects() {
        assertUpdateRejectedBeforeMutation(ModelConfigStatus.DEPRECATED, "已停用");
    }

    @Test
    void updateRejectedModelConfigResubmitsNewApprovalAndClearsRejectedConnectivityFacts() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ModelConfig rejected = config(ModelConfigStatus.REJECTED, ModelConnectivityStatus.FAILED);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(rejected));
        when(configs.findByProviderAndCode(TenantId.of("2001"), "3001", "gpt-4.1-mini"))
                .thenReturn(Optional.of(rejected));
        when(runtimeOptions.validate(any(ModelProvider.class), any(ModelConfig.class)))
                .thenReturn(ModelRuntimeValidation.passed());
        when(configs.compareAndSet(any(ModelConfig.class), eq(0), eq(ModelConfigStatus.REJECTED)))
                .thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        when(approvalPort.requestApproval(any(ModelConfigChange.class)))
                .thenAnswer(invocation -> invocation.<ModelConfigChange>getArgument(0).changeId());
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        ModelConfigDetailResult result = service.updateModelConfig(updateCommand(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.4,
                "maxTokens", 2048
        )));

        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.connectivityStatus()).isEqualTo("NOT_TESTED");
        ArgumentCaptor<ModelConfig> saved = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSet(saved.capture(), eq(0), eq(ModelConfigStatus.REJECTED));
        assertThat(saved.getValue().connectivityCheckedAt()).isNull();
        assertThat(saved.getValue().connectivityMessage()).isEmpty();
        ArgumentCaptor<ModelConfig> validated = ArgumentCaptor.forClass(ModelConfig.class);
        verify(runtimeOptions).validate(org.mockito.ArgumentMatchers.eq(provider()), validated.capture());
        assertThat(validated.getValue().approvalEvidenceDigest()).isNull();
        assertThat(validated.getValue().modelConfigId()).isEqualTo(saved.getValue().modelConfigId());
        ArgumentCaptor<ModelConfigChange> change = ArgumentCaptor.forClass(ModelConfigChange.class);
        verify(approvalPort).requestApproval(change.capture());
        verify(usagePolicyPublisher).publish(provider(), saved.getValue());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(audit.capture());
        assertThat(audit.getValue().approvalReferenceId()).isEqualTo(change.getValue().changeId());
    }

    @Test
    void disableModelConfigDeprecatesModelAndPublishesAuditEvent() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001")).thenReturn(Optional.of(config()));
        when(configs.compareAndSet(any(ModelConfig.class), eq(0), eq(ModelConfigStatus.ACTIVE)))
                .thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                mock(ModelRuntimeOptionsPort.class),
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        ModelConfigDetailResult result = service.disableModelConfig(new DisableModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "3001",
                "4001",
                "切换到已验证的新模型",
                0
        ));

        assertThat(result.status()).isEqualTo("DEPRECATED");
        ArgumentCaptor<ModelConfig> configCaptor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSet(configCaptor.capture(), eq(0), eq(ModelConfigStatus.ACTIVE));
        assertThat(configCaptor.getValue().status()).isEqualTo(ModelConfigStatus.DEPRECATED);
        verify(usagePolicyPublisher).publish(provider(), configCaptor.getValue());
        verifyNoInteractions(approvalPort);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().resourceType()).isEqualTo("MODEL_CONFIG");
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("4001");
        assertThat(auditCaptor.getValue().action()).isEqualTo("MODEL_CONFIG_DISABLE");
        assertThat(auditCaptor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(auditCaptor.getValue().detailAttributes())
                .containsEntry("reason", "切换到已验证的新模型");
    }

    @Test
    void disableModelConfigRejectsPendingApprovalBeforeMutationSideEffects() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001"))
                .thenReturn(Optional.of(config(ModelConfigStatus.PENDING_APPROVAL, ModelConnectivityStatus.NOT_TESTED)));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                mock(ModelRuntimeOptionsPort.class),
                mock(ModelConfigApprovalPort.class),
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.disableModelConfig(new DisableModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "3001",
                "4001",
                "不应绕过待审批状态",
                0
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("待审批")
                .hasMessageContaining("不可停用");

        verify(configs, never()).compareAndSet(any(), anyInt(), any());
        verifyNoInteractions(usagePolicyPublisher, auditPublisher);
    }

    @Test
    void listModelConfigsUsesQueryRepositoryDirectlyForReadModel() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelConfigQueryRepository configQueries = mock(ModelConfigQueryRepository.class);
        ModelConfigDetailResult detail = new ModelConfigDetailResult(
                "3001",
                "4001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                "0.0004",
                "0.0016",
                "openai-compatible",
                "0.2",
                null,
                1024,
                "ACTIVE",
                null,
                "PASSED",
                "连通性正常",
                0
        );
        when(configQueries.findDetailsByProviderId(TenantId.of("2001"), "3001")).thenReturn(java.util.List.of(detail));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                configQueries,
                mock(ModelRuntimeOptionsPort.class),
                mock(ModelConfigApprovalPort.class),
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                mock(ModelUsagePolicyPublisher.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThat(service.listModelConfigs(TenantId.of("2001"), "3001")).containsExactly(detail);
        verify(configQueries).findDetailsByProviderId(TenantId.of("2001"), "3001");
        verify(configs, never()).findByProviderId(any(TenantId.class), any(String.class));
        verifyNoInteractions(providers);
    }

    private static CreateModelConfigCommand command() {
        return command(Map.of("providerKind", "openai-compatible", "temperature", 0.2));
    }

    private static CreateModelConfigCommand command(Map<String, Object> options) {
        return new CreateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "model-request-test",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                "CHAT",
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                options
        );
    }

    private static UpdateModelConfigCommand updateCommand(Map<String, Object> options) {
        return new UpdateModelConfigCommand(
                TenantId.of("2001"),
                "1001",
                "3001",
                "4001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini 更新版",
                "CHAT",
                64000,
                null,
                new BigDecimal("0.0003"),
                new BigDecimal("0.0012"),
                options,
                0
        );
    }

    private static void assertUpdateRejectedBeforeMutation(ModelConfigStatus status, String expectedMessage) {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelRuntimeOptionsPort runtimeOptions = mock(ModelRuntimeOptionsPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providers.findById(TenantId.of("2001"), "3001")).thenReturn(Optional.of(provider()));
        when(configs.findById(TenantId.of("2001"), "3001", "4001"))
                .thenReturn(Optional.of(config(status, ModelConnectivityStatus.NOT_TESTED)));
        ModelConfigApplicationService service = new ModelConfigApplicationService(
                providers,
                configs,
                mock(ModelConfigQueryRepository.class),
                runtimeOptions,
                approvalPort,
                new ModelConfigCreationService(),
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher,
                auditPublisher,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.updateModelConfig(updateCommand(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.4
        ))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(expectedMessage);

        verify(configs, never()).findByProviderAndCode(any(), any(), any());
        verify(configs, never()).compareAndSet(any(), anyInt(), any());
        verifyNoInteractions(runtimeOptions, approvalPort, usagePolicyPublisher, auditPublisher);
    }

    private static ModelProvider provider() {
        return provider(ModelProviderStatus.ACTIVE);
    }

    private static ModelProvider provider(ModelProviderStatus status) {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                status,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelProvider localProvider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "local-deterministic",
                "本地确定性运行时",
                "local://deterministic",
                "no_auth",
                null,
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelConfig config() {
        return config(ModelConfigStatus.ACTIVE, ModelConnectivityStatus.NOT_TESTED);
    }

    private static ModelConfig config(
            ModelConfigStatus status,
            ModelConnectivityStatus connectivityStatus
    ) {
        Instant checkedAt = connectivityStatus == ModelConnectivityStatus.NOT_TESTED
                ? null
                : Instant.parse("2026-05-25T02:30:00Z");
        String message = connectivityStatus == ModelConnectivityStatus.NOT_TESTED ? "" : "连接通过";
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                status,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible", "temperature", 0.2)),
                connectivityStatus,
                checkedAt,
                message,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }
}
