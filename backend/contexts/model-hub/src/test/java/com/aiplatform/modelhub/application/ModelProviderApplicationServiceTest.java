package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.application.command.CreateModelProviderCommand;
import com.aiplatform.modelhub.application.command.DisableModelProviderCommand;
import com.aiplatform.modelhub.application.query.ModelProviderQueryRepository;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;
import com.aiplatform.modelhub.application.result.ModelProviderDetailResult;
import com.aiplatform.modelhub.domain.ModelApprovalCancellation;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigChange;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderCreationService;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelConfigApprovalPort;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelSecretVaultPort;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import com.aiplatform.shared.security.net.StrictOutboundEndpointPolicy;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelProviderApplicationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-22T03:00:00Z"),
            ZoneOffset.UTC
    );

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void createProviderPersistsProviderStoresSecretAndRequestsApproval() {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-SERVICE-001");
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providerRepository.nextProviderId()).thenReturn("9001");
        when(providerRepository.insert(any(ModelProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(secretVault.store(any(TenantId.class), any(), any(), any()))
                .thenReturn(new ModelSecretRef("SEC-9001", "sk-***lain"));
        when(approvalPort.requestApproval(any(ModelConfigChange.class)))
                .thenAnswer(invocation -> invocation.<ModelConfigChange>getArgument(0).changeId());
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                publicEndpointPolicy(),
                FIXED_CLOCK
        );

        CreateModelProviderResult result = service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                "sk-live-plain"
        ));

        assertThat(result.providerId()).isEqualTo("9001");
        assertThat(result.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(result.approvalRequestId()).isNotBlank();

        ArgumentCaptor<ModelProvider> providerCaptor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(providerRepository).insert(providerCaptor.capture());
        ModelProvider provider = providerCaptor.getValue();
        assertThat(provider.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(provider.providerCode()).isEqualTo("openai");
        assertThat(provider.providerName()).isEqualTo("OpenAI 企业账号");
        assertThat(provider.endpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(provider.authType()).isEqualTo("api_key");
        assertThat(provider.secretRef()).isNull();
        assertThat(provider.status()).isEqualTo(ModelProviderStatus.PENDING_APPROVAL);
        assertThat(provider.createdAt()).isEqualTo(Instant.parse("2026-05-22T03:00:00Z"));

        ArgumentCaptor<TenantId> tenantCaptor = ArgumentCaptor.forClass(TenantId.class);
        ArgumentCaptor<String> providerIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> credentialCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> operatorCaptor = ArgumentCaptor.forClass(String.class);
        verify(secretVault).store(
                tenantCaptor.capture(),
                providerIdCaptor.capture(),
                credentialCaptor.capture(),
                operatorCaptor.capture()
        );
        assertThat(tenantCaptor.getValue()).isEqualTo(TenantId.of("2001"));
        assertThat(providerIdCaptor.getValue()).isEqualTo("9001");
        assertThat(credentialCaptor.getValue()).isEqualTo("sk-live-plain");
        assertThat(operatorCaptor.getValue()).isEqualTo("1001");

        ArgumentCaptor<ModelConfigChange> changeCaptor = ArgumentCaptor.forClass(ModelConfigChange.class);
        verify(approvalPort).requestApproval(changeCaptor.capture());
        assertThat(result.approvalRequestId()).isEqualTo(changeCaptor.getValue().changeId());
        assertThat(provider.approvalRequestId()).isEqualTo(changeCaptor.getValue().changeId());
        assertThat(changeCaptor.getValue().providerId()).isEqualTo("9001");
        assertThat(changeCaptor.getValue().operatorId()).isEqualTo("1001");
        assertThat(changeCaptor.getValue().traceId()).isEqualTo("TRACE-FILTER-MODEL-SERVICE-001");
        assertThat(changeCaptor.getValue().requiresApproval()).isTrue();

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("MODEL_PROVIDER_CREATE");
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("9001");
        assertThat(auditCaptor.getValue().traceId()).isEqualTo("TRACE-FILTER-MODEL-SERVICE-001");
    }

    @Test
    void createNoAuthProviderDoesNotStoreCredentialMaterial() {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-SERVICE-001");
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        when(providerRepository.nextProviderId()).thenReturn("9001");
        when(providerRepository.insert(any(ModelProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalPort.requestApproval(any(ModelConfigChange.class)))
                .thenAnswer(invocation -> invocation.<ModelConfigChange>getArgument(0).changeId());
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                mock(AuditEventPublisher.class),
                publicEndpointPolicy(),
                FIXED_CLOCK
        );

        CreateModelProviderResult result = service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "ollama",
                "Ollama 本地账号",
                "https://ollama.example.com",
                "no_auth",
                null
        ));

        assertThat(result.providerId()).isEqualTo("9001");

        ArgumentCaptor<ModelProvider> providerCaptor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(providerRepository).insert(providerCaptor.capture());
        assertThat(providerCaptor.getValue().authType()).isEqualTo("no_auth");
        assertThat(providerCaptor.getValue().secretRef()).isNull();
        verify(secretVault, never()).store(any(TenantId.class), any(), any(), any());

        ArgumentCaptor<ModelConfigChange> changeCaptor = ArgumentCaptor.forClass(ModelConfigChange.class);
        verify(approvalPort).requestApproval(changeCaptor.capture());
        assertThat(providerCaptor.getValue().approvalRequestId()).isEqualTo(changeCaptor.getValue().changeId());
        assertThat(changeCaptor.getValue().providerId()).isEqualTo("9001");
    }

    @Test
    void createsCanonicalLocalDeterministicProviderWithoutInvokingOutboundPolicy() {
        TraceContext.ensureTraceId("TRACE-LOCAL-DETERMINISTIC-001");
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        OutboundEndpointPolicy outboundEndpointPolicy = mock(OutboundEndpointPolicy.class);
        when(providerRepository.nextProviderId()).thenReturn("9002");
        when(providerRepository.insert(any(ModelProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(approvalPort.requestApproval(any(ModelConfigChange.class)))
                .thenAnswer(invocation -> invocation.<ModelConfigChange>getArgument(0).changeId());
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                outboundEndpointPolicy,
                FIXED_CLOCK
        );

        CreateModelProviderResult result = service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "local-deterministic",
                "本地确定性运行时",
                "local://deterministic",
                "no_auth",
                null
        ));

        assertThat(result.providerId()).isEqualTo("9002");
        ArgumentCaptor<ModelProvider> providerCaptor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(providerRepository).insert(providerCaptor.capture());
        assertThat(providerCaptor.getValue().endpoint()).isEqualTo("local://deterministic");
        assertThat(providerCaptor.getValue().authType()).isEqualTo("no_auth");
        verifyNoInteractions(outboundEndpointPolicy, secretVault);
    }

    @Test
    void rejectsLocalDeterministicProviderWhenDeploymentCapabilityIsDisabled() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        OutboundEndpointPolicy outboundEndpointPolicy = mock(OutboundEndpointPolicy.class);
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                outboundEndpointPolicy,
                FIXED_CLOCK,
                false
        );

        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "local-deterministic",
                "未授权的本地运行时",
                "local://deterministic",
                "no_auth",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前部署未启用");
        verifyNoInteractions(providerRepository, secretVault, approvalPort, auditPublisher, outboundEndpointPolicy);
    }

    @Test
    void rejectsNonCanonicalOrCredentialBackedLocalDeterministicProviderBeforeSideEffects() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        OutboundEndpointPolicy outboundEndpointPolicy = mock(OutboundEndpointPolicy.class);
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                outboundEndpointPolicy,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "local-deterministic",
                "错误地址的本地运行时",
                "http://local-deterministic",
                "no_auth",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local://deterministic");
        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "local-deterministic",
                "错误鉴权的本地运行时",
                "local://deterministic",
                "api_key",
                "must-not-be-stored"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_auth")
                .hasMessageNotContaining("must-not-be-stored");
        verifyNoInteractions(providerRepository, secretVault, approvalPort, auditPublisher, outboundEndpointPolicy);
    }

    @Test
    void rejectsRemovedOrUnknownProviderTypesBeforeOutboundValidationAndSideEffects() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        OutboundEndpointPolicy outboundEndpointPolicy = mock(OutboundEndpointPolicy.class);
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                outboundEndpointPolicy,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "zhipu-ai",
                "已移除供应商",
                "https://open.bigmodel.cn/api/paas/v4",
                "api_key",
                "must-not-be-stored"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂不支持模型供应商类型")
                .hasMessageNotContaining("must-not-be-stored");
        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "future-provider",
                "未知供应商",
                "https://future-provider.example/v1",
                "api_key",
                "must-not-be-stored"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂不支持模型供应商类型")
                .hasMessageNotContaining("must-not-be-stored");
        verifyNoInteractions(providerRepository, secretVault, approvalPort, auditPublisher, outboundEndpointPolicy);
    }

    @Test
    void rejectsProviderAuthMismatchBeforeOutboundValidationAndSideEffects() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvalPort = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        OutboundEndpointPolicy outboundEndpointPolicy = mock(OutboundEndpointPolicy.class);
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvalPort,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                outboundEndpointPolicy,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "bedrock",
                "错误鉴权的 Bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "api_key",
                "must-not-be-stored"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bedrock")
                .hasMessageContaining("aws_credentials")
                .hasMessageNotContaining("must-not-be-stored");
        assertThatThrownBy(() -> service.createProvider(new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "mistral-ai",
                "错误鉴权的 Mistral",
                "https://api.mistral.ai/v1",
                "no_auth",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mistral-ai")
                .hasMessageContaining("api_key");
        verifyNoInteractions(providerRepository, secretVault, approvalPort, auditPublisher, outboundEndpointPolicy);
    }

    @Test
    void acceptsCanonicalRuntimeProviderAuthMatrix() {
        OutboundEndpointPolicy outboundEndpointPolicy = mock(OutboundEndpointPolicy.class);
        when(outboundEndpointPolicy.validateAndNormalize(any()))
                .thenReturn(URI.create("https://provider.example/v1"));
        ModelProviderConnectionContract policy = new ModelProviderConnectionContract(outboundEndpointPolicy, true);

        Map.of(
                "openai-compatible", "api_key",
                "azure-openai", "api_key",
                "ollama", "no_auth",
                "mistral-ai", "api_key",
                "bedrock", "aws_credentials",
                "vertex-ai", "gcp_service_account"
        ).forEach((providerType, authType) -> assertThat(policy.validateAndNormalize(
                providerType,
                "https://provider.example/v1",
                authType
        )).isEqualTo("https://provider.example/v1"));
    }

    @Test
    void normalizesCreateProviderCommandFactsBeforeServiceUse() {
        CreateModelProviderCommand credentialCommand = new CreateModelProviderCommand(
                TenantId.of("2001"),
                " 1001 ",
                "provider-request-test",
                " openai ",
                " OpenAI 企业账号 ",
                " https://api.openai.example/v1 ",
                " API_KEY ",
                " *** "
        );
        CreateModelProviderCommand noAuthCommand = new CreateModelProviderCommand(
                TenantId.of("2001"),
                " 1001 ",
                "provider-request-test",
                " ollama ",
                " Ollama 本地账号 ",
                " http://localhost:11434 ",
                " no_auth ",
                " "
        );

        assertThat(credentialCommand.operatorId()).isEqualTo("1001");
        assertThat(credentialCommand.providerType()).isEqualTo("openai");
        assertThat(credentialCommand.providerName()).isEqualTo("OpenAI 企业账号");
        assertThat(credentialCommand.apiEndpoint()).isEqualTo("https://api.openai.example/v1");
        assertThat(credentialCommand.authType()).isEqualTo("api_key");
        assertThat(credentialCommand.credentialMaterialInput()).isEqualTo(" *** ");
        assertThat(noAuthCommand.credentialMaterialInput()).isNull();
    }

    @Test
    void listProvidersUsesQueryRepositoryDirectlyForReadModel() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelProviderQueryRepository providerQueries = mock(ModelProviderQueryRepository.class);
        ModelProviderDetailResult detail = new ModelProviderDetailResult(
                "9001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "ACTIVE",
                "REQ-3001",
                0
        );
        when(providerQueries.findAllDetails(TenantId.of("2001"))).thenReturn(java.util.List.of(detail));
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                providerQueries,
                mock(ModelSecretVaultPort.class),
                mock(ModelConfigApprovalPort.class),
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                mock(AuditEventPublisher.class),
                publicEndpointPolicy(),
                FIXED_CLOCK
        );

        assertThat(service.listProviders(TenantId.of("2001"))).containsExactly(detail);
        verify(providerQueries).findAllDetails(TenantId.of("2001"));
        verify(providerRepository, never()).findAll(any(TenantId.class));
    }

    @Test
    void disableProviderPersistsDisabledStatusAndPublishesAuditEvent() {
        TraceContext.ensureTraceId("TRACE-FILTER-MODEL-DISABLE-001");
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelProvider provider = new ModelProvider(
                TenantId.of("2001"),
                "9001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("SEC-9001", "sk-***live"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T02:00:00Z"),
                Instant.parse("2026-05-22T02:00:00Z")
        );
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ModelConfigRepository configRepository = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelConfig activeConfig = new ModelConfig(
                TenantId.of("2001"),
                "9101",
                "9001",
                "local-chat",
                "本地聊天模型",
                ModelType.CHAT,
                8192,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "local-deterministic")),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-22T02:30:00Z"),
                "连通",
                Instant.parse("2026-05-22T02:00:00Z"),
                Instant.parse("2026-05-22T02:30:00Z")
        );
        when(providerRepository.findById(TenantId.of("2001"), "9001")).thenReturn(java.util.Optional.of(provider));
        when(providerRepository.compareAndSet(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.ACTIVE)
        )).thenAnswer(invocation -> Optional.of(
                invocation.<ModelProvider>getArgument(0).withVersion(1)
        ));
        when(configRepository.findByProviderId(TenantId.of("2001"), "9001"))
                .thenReturn(java.util.List.of(activeConfig));
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                mock(ModelSecretVaultPort.class),
                mock(ModelConfigApprovalPort.class),
                new ModelProviderCreationService(),
                configRepository,
                usagePolicyPublisher,
                auditPublisher,
                publicEndpointPolicy(),
                FIXED_CLOCK
        );

        ModelProviderDetailResult result = service.disableProvider(new DisableModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "9001",
                "供应商合同已到期",
                0
        ));

        assertThat(result.providerId()).isEqualTo("9001");
        assertThat(result.status()).isEqualTo("DISABLED");

        ArgumentCaptor<ModelProvider> providerCaptor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(providerRepository).compareAndSet(
                providerCaptor.capture(), eq(0), eq(ModelProviderStatus.ACTIVE)
        );
        assertThat(providerCaptor.getValue().status()).isEqualTo(ModelProviderStatus.DISABLED);
        assertThat(providerCaptor.getValue().updatedAt()).isEqualTo(Instant.parse("2026-05-22T03:00:00Z"));
        verify(usagePolicyPublisher).publish(providerCaptor.getValue().withVersion(1), activeConfig);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().tenantId()).isEqualTo("2001");
        assertThat(auditCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(auditCaptor.getValue().resourceType()).isEqualTo("MODEL_PROVIDER");
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("9001");
        assertThat(auditCaptor.getValue().action()).isEqualTo("MODEL_PROVIDER_DISABLE");
        assertThat(auditCaptor.getValue().riskLevel()).isEqualTo("HIGH");
        assertThat(auditCaptor.getValue().traceId()).isEqualTo("TRACE-FILTER-MODEL-DISABLE-001");
        assertThat(auditCaptor.getValue().detailAttributes())
                .containsEntry("reason", "供应商合同已到期");
    }

    @Test
    void disablingPendingProviderDurablyRequestsApprovalCancellation() {
        TraceContext.ensureTraceId("TRACE-PROVIDER-APPROVAL-CANCEL-001");
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelProvider pending = new ModelProvider(
                TenantId.of("2001"),
                "9001",
                "openai",
                "待审批供应商",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("SEC-9001", "sk-***live"),
                ModelProviderStatus.PENDING_APPROVAL,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T02:00:00Z"),
                Instant.parse("2026-05-22T02:00:00Z")
        );
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ModelConfigApprovalPort approvals = mock(ModelConfigApprovalPort.class);
        when(providerRepository.findById(TenantId.of("2001"), "9001")).thenReturn(Optional.of(pending));
        when(providerRepository.compareAndSet(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL)
        )).thenAnswer(invocation -> Optional.of(invocation.<ModelProvider>getArgument(0).withVersion(1)));
        when(configs.findByProviderId(TenantId.of("2001"), "9001")).thenReturn(java.util.List.of());
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                mock(ModelSecretVaultPort.class),
                approvals,
                new ModelProviderCreationService(),
                configs,
                usagePolicyPublisher,
                auditPublisher,
                publicEndpointPolicy(),
                FIXED_CLOCK
        );

        ModelProviderDetailResult result = service.disableProvider(new DisableModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "9001",
                "地址录入错误，撤销本次审批",
                0
        ));

        assertThat(result.status()).isEqualTo("DISABLED");
        ArgumentCaptor<ModelProvider> providerCaptor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(providerRepository).compareAndSet(
                providerCaptor.capture(), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL)
        );
        assertThat(providerCaptor.getValue().status()).isEqualTo(ModelProviderStatus.DISABLED);
        assertThat(providerCaptor.getValue().approvalRequestId()).isEqualTo("REQ-TEST");
        assertThat(providerCaptor.getValue().hasApprovalEvidence()).isFalse();

        ArgumentCaptor<ModelApprovalCancellation> cancellationCaptor =
                ArgumentCaptor.forClass(ModelApprovalCancellation.class);
        verify(approvals).cancelApproval(cancellationCaptor.capture());
        assertThat(cancellationCaptor.getValue()).isEqualTo(new ModelApprovalCancellation(
                TenantId.of("2001"),
                "REQ-TEST",
                ModelConfigChange.RESOURCE_TYPE_MODEL_PROVIDER,
                "9001",
                "1001",
                "地址录入错误，撤销本次审批",
                "TRACE-PROVIDER-APPROVAL-CANCEL-001",
                Instant.parse("2026-05-22T03:00:00Z")
        ));

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action())
                .isEqualTo("MODEL_PROVIDER_APPROVAL_CANCELLATION_REQUESTED");
        assertThat(auditCaptor.getValue().approvalReferenceId()).isEqualTo("REQ-TEST");
        assertThat(auditCaptor.getValue().traceId()).isEqualTo("TRACE-PROVIDER-APPROVAL-CANCEL-001");
        assertThat(auditCaptor.getValue().detailAttributes())
                .containsEntry("reason", "地址录入错误，撤销本次审批")
                .containsEntry("previousStatus", "PENDING_APPROVAL")
                .containsEntry("outcome", "APPROVAL_CANCELLATION_REQUESTED");
        verifyNoInteractions(usagePolicyPublisher);
    }

    @Test
    void concurrentPendingProviderCancellationFailsClosedBeforePolicyOrAudit() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelProvider pending = new ModelProvider(
                TenantId.of("2001"),
                "9001",
                "openai",
                "待审批供应商",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("SEC-9001", "sk-***live"),
                ModelProviderStatus.PENDING_APPROVAL,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-22T02:00:00Z"),
                Instant.parse("2026-05-22T02:00:00Z")
        ).withVersion(3);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(providerRepository.findById(TenantId.of("2001"), "9001")).thenReturn(Optional.of(pending));
        when(providerRepository.compareAndSet(
                any(ModelProvider.class), eq(3), eq(ModelProviderStatus.PENDING_APPROVAL)
        )).thenReturn(Optional.empty());
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                mock(ModelSecretVaultPort.class),
                mock(ModelConfigApprovalPort.class),
                new ModelProviderCreationService(),
                configs,
                usagePolicyPublisher,
                auditPublisher,
                publicEndpointPolicy(),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.disableProvider(new DisableModelProviderCommand(
                TenantId.of("2001"), "1001", "9001", "撤销过期申请", 3
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("expectedVersion 不再有效");

        verifyNoInteractions(configs, usagePolicyPublisher, auditPublisher);
    }

    @Test
    void createProviderRejectsSsrfEndpointBeforeAnyRepositoryOrSecretSideEffect() {
        ModelProviderRepository providerRepository = mock(ModelProviderRepository.class);
        ModelSecretVaultPort secretVault = mock(ModelSecretVaultPort.class);
        ModelConfigApprovalPort approvals = mock(ModelConfigApprovalPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        StrictOutboundEndpointPolicy privateAddressPolicy = new StrictOutboundEndpointPolicy(host ->
                new InetAddress[]{InetAddress.getByName("169.254.169.254")}
        );
        ModelProviderApplicationService service = new ModelProviderApplicationService(
                providerRepository,
                mock(ModelProviderQueryRepository.class),
                secretVault,
                approvals,
                new ModelProviderCreationService(),
                mock(ModelConfigRepository.class),
                mock(ModelUsagePolicyPublisher.class),
                auditPublisher,
                privateAddressPolicy,
                FIXED_CLOCK
        );

        CreateModelProviderCommand command = new CreateModelProviderCommand(
                TenantId.of("2001"),
                "1001",
                "provider-request-test",
                "openai",
                "恶意供应商",
                "https://metadata.attacker.example/v1",
                "api_key",
                "sk-must-not-be-stored"
        );

        assertThatThrownBy(() -> service.createProvider(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非公网地址")
                .hasMessageNotContaining("sk-must-not-be-stored");
        verifyNoInteractions(providerRepository, secretVault, approvals, auditPublisher);
    }

    private static OutboundEndpointPolicy publicEndpointPolicy() {
        return new StrictOutboundEndpointPolicy(host ->
                new InetAddress[]{InetAddress.getByName("93.184.216.34")}
        );
    }

}
