package com.aiplatform.modelhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelApprovalEvidence;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.domain.port.ModelConfigRepository;
import com.aiplatform.modelhub.domain.port.ModelProviderRepository;
import com.aiplatform.modelhub.domain.port.ModelUsagePolicyPublisher;
import com.aiplatform.modelhub.domain.service.ModelConfigActivationPolicy;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelApprovalDecisionApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant DECIDED_AT = Instant.parse("2026-05-25T08:00:00Z");
    private static final ApprovalEvidenceEnvelope EVIDENCE = ApprovalEvidenceEnvelope.create(
            Map.of("contract", "model-approval-decision-test")
    );

    @Test
    void providerApprovalNeverActivatesPendingModelEvenWhenConnectivityWasRecordedAsPassed() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(pendingProvider()));
        when(configs.findByProviderId(TENANT_ID, "3001")).thenReturn(List.of(passedPendingConfig()));
        when(providers.compareAndSetApprovalDecision(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenAnswer(invocation -> Optional.of(invocation.<ModelProvider>getArgument(0)));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyProviderDecision(service, TENANT_ID, "3001", "REQ-TEST", true, DECIDED_AT);

        ArgumentCaptor<ModelProvider> providerCaptor = ArgumentCaptor.forClass(ModelProvider.class);
        verify(providers).compareAndSetApprovalDecision(
                providerCaptor.capture(), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        assertThat(providerCaptor.getValue().status()).isEqualTo(ModelProviderStatus.ACTIVE);

        verify(usagePolicyPublisher).publish(providerCaptor.getValue(), passedPendingConfig());
    }

    @Test
    void delayedApprovalOrRejectionCannotOverrideCancelledProviderOrPublishModelPolicies() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(disabledProvider()));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyProviderDecision(service, TENANT_ID, "3001", "REQ-TEST", true, DECIDED_AT);
        applyProviderDecision(service, TENANT_ID, "3001", "REQ-TEST", false, DECIDED_AT.plusSeconds(1));

        verify(providers, times(2)).findById(TENANT_ID, "3001");
        verifyNoMoreInteractions(providers);
        verifyNoInteractions(configs, usagePolicyPublisher);
    }

    @Test
    void staleProviderDecisionCannotAuthorizeTheCurrentPendingRequest() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(pendingProvider()));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyProviderDecision(service, TENANT_ID, "3001", "REQ-STALE-A", true, DECIDED_AT);

        verify(providers).findById(TENANT_ID, "3001");
        verifyNoMoreInteractions(providers);
        verifyNoInteractions(configs, usagePolicyPublisher);
    }

    @Test
    void sameProviderRequestAndTargetWithDifferentEvidenceDigestProducesZeroMutationOrPolicy() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(pendingProvider()));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        service.applyProviderDecision(
                TENANT_ID,
                "3001",
                "REQ-TEST",
                EVIDENCE.schema(),
                "c".repeat(64),
                pendingProvider().approvalTargetDigest(),
                true,
                DECIDED_AT
        );

        verify(providers).findById(TENANT_ID, "3001");
        verifyNoMoreInteractions(providers);
        verifyNoInteractions(configs, usagePolicyPublisher);
    }

    @Test
    void concurrentProviderChangeAfterReadCannotBeResurrectedOrPublishModelPolicies() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(pendingProvider()));
        when(providers.compareAndSetApprovalDecision(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenReturn(Optional.empty());
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyProviderDecision(service, TENANT_ID, "3001", "REQ-TEST", true, DECIDED_AT);

        verify(providers).findById(TENANT_ID, "3001");
        verify(providers).compareAndSetApprovalDecision(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        verifyNoMoreInteractions(providers);
        verifyNoInteractions(configs, usagePolicyPublisher);
    }

    @Test
    void approvedModelDecisionResetsPreApprovalConnectivityAndRequiresAProtectedRetest() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(configs.findByModelConfigId(TENANT_ID, "4001")).thenReturn(Optional.of(passedPendingConfig()));
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(activeProvider()));
        when(configs.compareAndSetApprovalDecision(
                any(ModelConfig.class), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyModelConfigDecision(service, TENANT_ID, "4001", "REQ-TEST", true, DECIDED_AT);

        verify(configs).findByModelConfigId(TENANT_ID, "4001");
        verify(providers).findById(TENANT_ID, "3001");
        ArgumentCaptor<ModelConfig> configCaptor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSetApprovalDecision(
                configCaptor.capture(), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        assertThat(configCaptor.getValue().status()).isEqualTo(ModelConfigStatus.INACTIVE);
        assertThat(configCaptor.getValue().connectivityStatus()).isEqualTo(ModelConnectivityStatus.NOT_TESTED);
        assertThat(configCaptor.getValue().connectivityCheckedAt()).isNull();
        verify(usagePolicyPublisher).publish(activeProvider(), configCaptor.getValue());
    }

    @Test
    void staleModelConfigDecisionCannotAuthorizeTheCurrentPendingRequest() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(configs.findByModelConfigId(TENANT_ID, "4001")).thenReturn(Optional.of(passedPendingConfig()));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyModelConfigDecision(service, TENANT_ID, "4001", "REQ-STALE-A", true, DECIDED_AT);

        verify(configs).findByModelConfigId(TENANT_ID, "4001");
        verifyNoMoreInteractions(configs);
        verifyNoInteractions(providers, usagePolicyPublisher);
    }

    @Test
    void sameModelRequestAndTargetWithDifferentEvidenceDigestProducesZeroMutationOrPolicy() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(configs.findByModelConfigId(TENANT_ID, "4001")).thenReturn(Optional.of(passedPendingConfig()));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        service.applyModelConfigDecision(
                TENANT_ID,
                "4001",
                "REQ-TEST",
                EVIDENCE.schema(),
                "c".repeat(64),
                passedPendingConfig().approvalTargetDigest(),
                true,
                DECIDED_AT
        );

        verify(configs).findByModelConfigId(TENANT_ID, "4001");
        verifyNoMoreInteractions(configs);
        verifyNoInteractions(providers, usagePolicyPublisher);
    }

    @Test
    void concurrentModelConfigChangeAfterReadCannotBeResurrectedOrPublishUsagePolicy() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(configs.findByModelConfigId(TENANT_ID, "4001")).thenReturn(Optional.of(passedPendingConfig()));
        when(configs.compareAndSetApprovalDecision(
                any(ModelConfig.class), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenReturn(Optional.empty());
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyModelConfigDecision(service, TENANT_ID, "4001", "REQ-TEST", true, DECIDED_AT);

        verify(configs).findByModelConfigId(TENANT_ID, "4001");
        verify(configs).compareAndSetApprovalDecision(
                any(ModelConfig.class), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        verifyNoMoreInteractions(configs);
        verifyNoInteractions(providers, usagePolicyPublisher);
    }

    @Test
    void rejectedModelConfigDecisionPublishesDisabledUsagePolicy() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(configs.findByModelConfigId(TENANT_ID, "4001")).thenReturn(Optional.of(passedPendingConfig()));
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(activeProvider()));
        when(configs.compareAndSetApprovalDecision(
                any(ModelConfig.class), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyModelConfigDecision(service, TENANT_ID, "4001", "REQ-TEST", false, DECIDED_AT);

        ArgumentCaptor<ModelConfig> configCaptor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSetApprovalDecision(
                configCaptor.capture(), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        assertThat(configCaptor.getValue().status()).isEqualTo(ModelConfigStatus.REJECTED);
        assertThat(configCaptor.getValue().connectivityStatus()).isEqualTo(ModelConnectivityStatus.NOT_TESTED);
        verify(usagePolicyPublisher).publish(activeProvider(), configCaptor.getValue());
    }

    @Test
    void applyModelConfigDecisionTransitionsApprovedButUntestedConfigToInactive() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(configs.findByModelConfigId(TENANT_ID, "4002")).thenReturn(Optional.of(notTestedPendingConfig("4002")));
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(activeProvider()));
        when(configs.compareAndSetApprovalDecision(
                any(ModelConfig.class), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenAnswer(invocation -> Optional.of(invocation.<ModelConfig>getArgument(0)));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyModelConfigDecision(service, TENANT_ID, "4002", "REQ-TEST", true, DECIDED_AT);

        ArgumentCaptor<ModelConfig> configCaptor = ArgumentCaptor.forClass(ModelConfig.class);
        verify(configs).compareAndSetApprovalDecision(
                configCaptor.capture(), eq(0), eq(ModelConfigStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        assertThat(configCaptor.getValue().status()).isEqualTo(ModelConfigStatus.INACTIVE);
        assertThat(configCaptor.getValue().connectivityStatus()).isEqualTo(ModelConnectivityStatus.NOT_TESTED);
        verify(usagePolicyPublisher).publish(activeProvider(), configCaptor.getValue());
    }

    @Test
    void rejectsApprovalDecisionWithoutOccurredAtInsteadOfCurrentTimeFallback() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        assertThatThrownBy(() -> applyProviderDecision(service, TENANT_ID, "3001", "REQ-TEST", true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审批决策事件发生时间不能为空");

        verifyNoInteractions(providers, configs, usagePolicyPublisher);
    }

    @Test
    void rejectsApprovalDecisionWithoutTenantIdInsteadOfJdkNullPointerException() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        assertThatThrownBy(() -> applyProviderDecision(service, null, "3001", "REQ-TEST", true, DECIDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");

        verifyNoInteractions(providers, configs, usagePolicyPublisher);
    }

    @Test
    void normalizesProviderIdBeforeRepositoryUse() {
        ModelProviderRepository providers = mock(ModelProviderRepository.class);
        ModelConfigRepository configs = mock(ModelConfigRepository.class);
        ModelUsagePolicyPublisher usagePolicyPublisher = mock(ModelUsagePolicyPublisher.class);
        when(providers.findById(TENANT_ID, "3001")).thenReturn(Optional.of(pendingProvider()));
        when(configs.findByProviderId(TENANT_ID, "3001")).thenReturn(List.of());
        when(providers.compareAndSetApprovalDecision(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        )).thenAnswer(invocation -> Optional.of(invocation.<ModelProvider>getArgument(0)));
        ModelApprovalDecisionApplicationService service = new ModelApprovalDecisionApplicationService(
                providers,
                configs,
                new ModelConfigActivationPolicy(),
                usagePolicyPublisher
        );

        applyProviderDecision(service, TENANT_ID, " 3001 ", " REQ-TEST ", false, DECIDED_AT);

        verify(providers).findById(TENANT_ID, "3001");
        verify(providers).compareAndSetApprovalDecision(
                any(ModelProvider.class), eq(0), eq(ModelProviderStatus.PENDING_APPROVAL),
                eq("REQ-TEST"), eq(EVIDENCE.digest()), anyString()
        );
        verify(configs).findByProviderId(TENANT_ID, "3001");
    }

    private static void applyProviderDecision(
            ModelApprovalDecisionApplicationService service,
            TenantId tenantId,
            String providerId,
            String approvalRequestId,
            boolean approved,
            Instant occurredAt
    ) {
        service.applyProviderDecision(
                tenantId,
                providerId,
                approvalRequestId,
                EVIDENCE.schema(),
                EVIDENCE.digest(),
                pendingProvider().approvalTargetDigest(),
                approved,
                occurredAt
        );
    }

    private static void applyModelConfigDecision(
            ModelApprovalDecisionApplicationService service,
            TenantId tenantId,
            String modelConfigId,
            String approvalRequestId,
            boolean approved,
            Instant occurredAt
    ) {
        ModelConfig pending = "4001".equals(modelConfigId)
                ? passedPendingConfig()
                : notTestedPendingConfig(modelConfigId);
        service.applyModelConfigDecision(
                tenantId,
                modelConfigId,
                approvalRequestId,
                EVIDENCE.schema(),
                EVIDENCE.digest(),
                pending.approvalTargetDigest(),
                approved,
                occurredAt
        );
    }

    private static ModelProvider pendingProvider() {
        ModelProvider provider = new ModelProvider(
                TENANT_ID,
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                ModelProviderStatus.PENDING_APPROVAL,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
        return provider.withApprovalEvidence(EVIDENCE, ModelApprovalEvidence.targetDigestForProvider(provider));
    }

    private static ModelProvider activeProvider() {
        return new ModelProvider(
                TENANT_ID,
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static ModelProvider disabledProvider() {
        return new ModelProvider(
                TENANT_ID,
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                ModelProviderStatus.DISABLED,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:45:00Z")
        );
    }

    private static ModelConfig passedPendingConfig() {
        ModelConfig config = new ModelConfig(
                TENANT_ID,
                "4001",
                "3001",
                "bge-m3",
                "BGE M3",
                ModelType.EMBEDDING,
                8192,
                1536,
                new BigDecimal("0.0001"),
                BigDecimal.ZERO,
                ModelConfigStatus.PENDING_APPROVAL,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.PASSED,
                Instant.parse("2026-05-25T07:30:00Z"),
                "连通性通过",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:30:00Z")
        );
        return config.withApprovalEvidence(EVIDENCE, ModelApprovalEvidence.targetDigestForModelConfig(config));
    }

    private static ModelConfig notTestedPendingConfig(String modelConfigId) {
        ModelConfig config = new ModelConfig(
                TENANT_ID,
                modelConfigId,
                "3001",
                "gpt-4.1-mini",
                "GPT 4.1 Mini",
                ModelType.CHAT,
                128000,
                null,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                ModelConfigStatus.PENDING_APPROVAL,
                "REQ-TEST",
                new ModelRuntimeOptions(Map.of("providerKind", "openai-compatible")),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-05-25T07:00:00Z"),
                Instant.parse("2026-05-25T07:00:00Z")
        );
        return config.withApprovalEvidence(EVIDENCE, ModelApprovalEvidence.targetDigestForModelConfig(config));
    }
}
