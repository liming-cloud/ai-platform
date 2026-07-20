package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ModelApprovalEvidenceTest {
    private static final TenantId TENANT = TenantId.of("2001");
    private static final Instant REQUESTED_AT = Instant.parse("2026-07-20T08:00:00Z");

    @Test
    void providerEvidenceContainsReviewableConnectionFactsButNoSecretMetadata() {
        ModelProvider provider = new ModelProvider(
                TENANT,
                "101",
                "openai-compatible",
                "Production OpenAI",
                "https://api.example.com",
                "api_key",
                new ModelSecretRef("secret-77", "****secret-suffix"),
                ModelProviderStatus.PENDING_APPROVAL,
                "REQ-PROVIDER-101",
                true,
                REQUESTED_AT,
                REQUESTED_AT
        );

        ModelApprovalEvidence evidence = ModelApprovalEvidence.forProviderCreation(
                provider,
                true,
                "operator-9",
                REQUESTED_AT
        );

        assertThat(evidence.envelope().facts())
                .containsEntry("action", "MODEL_PROVIDER_CREATE")
                .containsEntry("approvalRequestId", "REQ-PROVIDER-101")
                .containsEntry("expectedPostDecisionState", "ACTIVE");
        assertThat(evidence.envelope().canonicalFacts())
                .contains("https://api.example.com")
                .doesNotContain("secret-77", "secret-suffix", "apiKey", "credentialMaterial");
        assertThat(evidence.targetDigest()).hasSize(64);
        assertThat(ModelApprovalEvidence.targetDigestForProvider(provider)).isEqualTo(evidence.targetDigest());
    }

    @Test
    void modelUpdateEvidenceIncludesTypedRuntimeOptionsExactDiffAndStableTargetDigest() {
        ModelProvider provider = activeProvider();
        Map<String, Object> beforeOptions = new LinkedHashMap<>();
        beforeOptions.put("providerKind", "azure-openai");
        beforeOptions.put("temperature", new BigDecimal("0.20"));
        beforeOptions.put("stopSequences", List.of("END", "STOP"));
        beforeOptions.put("deploymentName", "chat-prod");
        beforeOptions.put("apiVersion", "2026-06-01");
        beforeOptions.put("serviceVersion", "V1");
        beforeOptions.put("user", "tenant-runtime");
        beforeOptions.put("reasoningEffort", "medium");

        Map<String, Object> afterOptions = new LinkedHashMap<>();
        afterOptions.put("reasoningEffort", "high");
        afterOptions.put("user", "tenant-runtime");
        afterOptions.put("serviceVersion", "V1");
        afterOptions.put("apiVersion", "2026-06-01");
        afterOptions.put("deploymentName", "chat-prod");
        afterOptions.put("stopSequences", List.of("STOP", "END"));
        afterOptions.put("temperature", 0.7d);
        afterOptions.put("providerKind", "azure-openai");

        ModelConfig before = config("Old model", ModelConfigStatus.INACTIVE, "REQ-OLD", beforeOptions);
        ModelConfig target = config("Reviewed model", ModelConfigStatus.PENDING_APPROVAL, "REQ-CONFIG-201", afterOptions);

        ModelApprovalEvidence evidence = ModelApprovalEvidence.forModelConfigUpdate(
                provider,
                before,
                target,
                "operator-9",
                REQUESTED_AT
        );

        @SuppressWarnings("unchecked")
        List<String> changedFields = (List<String>) evidence.envelope().facts().get("changedFields");
        @SuppressWarnings("unchecked")
        Map<String, Object> targetFacts = (Map<String, Object>) evidence.envelope().facts().get("target");
        assertThat(changedFields).containsExactlyInAnyOrder(
                "modelName",
                "runtimeOptions.reasoningEffort",
                "runtimeOptions.stopSequences",
                "runtimeOptions.temperature"
        );
        assertThat(evidence.envelope().canonicalFacts())
                .contains("contextWindow", "inputPricePer1k", "outputPricePer1k", "deploymentName", "reasoningEffort");
        assertThat(targetFacts).containsEntry("inputPricePer1k", "0.012")
                .containsEntry("outputPricePer1k", "0.034");
        assertThat(ModelApprovalEvidence.targetDigestForModelConfig(target))
                .isEqualTo(evidence.targetDigest());
    }

    @Test
    void evidenceProjectionRejectsUnknownRuntimeOptionsEvenAfterCallerValidationIsBypassed() {
        ModelProvider provider = activeProvider();
        ModelConfig target = config(
                "Unsafe model",
                ModelConfigStatus.PENDING_APPROVAL,
                "REQ-CONFIG-UNSAFE",
                Map.of("providerKind", "azure-openai", "credentialMaterial", "must-never-be-reviewed")
        );

        assertThatThrownBy(() -> ModelApprovalEvidence.forModelConfigCreation(
                provider,
                target,
                "operator-9",
                REQUESTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知或敏感");
    }

    @Test
    void allowedRuntimeOptionKeysCannotSmuggleNestedSecretsOrUnboundedCollections() {
        ModelProvider provider = activeProvider();
        Map<String, Object> nestedSecret = new LinkedHashMap<>();
        nestedSecret.put("providerKind", "azure-openai");
        nestedSecret.put("temperature", Map.of("apiKey", "must-never-enter-evidence"));
        Map<String, Object> oversizedStops = new LinkedHashMap<>();
        oversizedStops.put("providerKind", "azure-openai");
        oversizedStops.put("stopSequences", IntStream.range(0, 65).mapToObj(String::valueOf).toList());

        assertThatThrownBy(() -> ModelApprovalEvidence.forModelConfigCreation(
                provider,
                config("Nested secret", ModelConfigStatus.PENDING_APPROVAL, "REQ-NESTED", nestedSecret),
                "operator-9",
                REQUESTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temperature").hasMessageContaining("数字");
        assertThatThrownBy(() -> ModelApprovalEvidence.forModelConfigCreation(
                provider,
                config("Oversized list", ModelConfigStatus.PENDING_APPROVAL, "REQ-LARGE", oversizedStops),
                "operator-9",
                REQUESTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("条目数量");
    }

    @Test
    void approvalRequestTimeUsesPostgresMicrosecondPrecisionBeforeDigesting() {
        Instant nanosecondClockValue = Instant.parse("2026-07-20T08:00:00.123456789Z");
        ModelProvider provider = activeProvider();

        ModelConfigChange change = ModelConfigChange.forModelConfigCreation(
                provider,
                config("Canonical time", ModelConfigStatus.PENDING_APPROVAL, "REQ-TIME", Map.of(
                        "providerKind", "azure_openai"
                )),
                "operator-9",
                "trace-time",
                nanosecondClockValue
        );

        assertThat(change.requestedAt()).isEqualTo(Instant.parse("2026-07-20T08:00:00.123456Z"));
        assertThat(change.approvalEvidence().facts())
                .containsEntry("requestedAt", "2026-07-20T08:00:00.123456Z");
    }

    @Test
    void bedrockCohereEvidenceBindsTheCanonicalTruncateEnumAsAString() {
        ModelProvider provider = new ModelProvider(
                TENANT,
                "301",
                "aws_bedrock",
                "Amazon Bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "aws_credentials",
                new ModelSecretRef("secret-aws", "****suffix"),
                ModelProviderStatus.ACTIVE,
                "REQ-PROVIDER-BEDROCK",
                true,
                REQUESTED_AT.minusSeconds(60),
                REQUESTED_AT.minusSeconds(30)
        );
        ModelConfig target = new ModelConfig(
                TENANT,
                "401",
                "301",
                "cohere.embed-multilingual-v3",
                "Cohere multilingual",
                ModelType.EMBEDDING,
                8192,
                1024,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ModelConfigStatus.PENDING_APPROVAL,
                "REQ-CONFIG-BEDROCK",
                new ModelRuntimeOptions(Map.of(
                        "providerKind", "bedrock",
                        "region", "us-east-1",
                        "timeoutSeconds", new BigDecimal("15"),
                        "embeddingProvider", "cohere",
                        "inputType", "SEARCH_DOCUMENT",
                        "truncate", "END"
                )),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                REQUESTED_AT.minusSeconds(10),
                REQUESTED_AT
        );

        ModelApprovalEvidence evidence = ModelApprovalEvidence.forModelConfigCreation(
                provider,
                target,
                "operator-9",
                REQUESTED_AT
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> evidenceTarget = (Map<String, Object>) evidence.envelope().facts().get("target");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimeOptions = (Map<String, Object>) evidenceTarget.get("runtimeOptions");
        assertThat(runtimeOptions).containsEntry("providerKind", "bedrock")
                .containsEntry("truncate", "END")
                .containsEntry("timeoutSeconds", new BigDecimal("15"));
    }

    @Test
    void providerAndModelEvidenceRejectsSecretsInNamesEndpointsOptionsAndHistoricalMessages() {
        ModelProvider unsafeProvider = new ModelProvider(
                TENANT,
                "101",
                "azure-openai",
                "api_key=super-secret-value-123456",
                "https://azure.example.com",
                "api_key",
                new ModelSecretRef("secret-77", "****suffix"),
                ModelProviderStatus.PENDING_APPROVAL,
                "REQ-UNSAFE-PROVIDER",
                true,
                REQUESTED_AT,
                REQUESTED_AT
        );
        assertThatThrownBy(() -> ModelApprovalEvidence.forProviderCreation(
                unsafeProvider,
                true,
                "operator-9",
                REQUESTED_AT
        )).hasMessageContaining("疑似包含凭证");

        ModelConfig historical = config(
                "Safe model",
                ModelConfigStatus.INACTIVE,
                "REQ-OLD",
                Map.of("providerKind", "azure-openai")
        ).withConnectivity(
                new ModelRuntimeConnectivity(
                        false,
                        ModelConnectivityStatus.FAILED,
                        "authorization=Bearer eyJhbGciOiJIUzI1NiJ9.abcdefghijklmno.pqrstuvwxyz12345"
                ),
                REQUESTED_AT.minusSeconds(5),
                REQUESTED_AT.minusSeconds(5)
        );
        ModelConfig target = config(
                "Reviewed model",
                ModelConfigStatus.PENDING_APPROVAL,
                "REQ-UNSAFE-CONNECTIVITY",
                Map.of("providerKind", "azure-openai")
        );
        assertThatThrownBy(() -> ModelApprovalEvidence.forModelConfigUpdate(
                activeProvider(),
                historical,
                target,
                "operator-9",
                REQUESTED_AT
        )).hasMessageContaining("疑似包含凭证");
    }

    private static ModelProvider activeProvider() {
        return new ModelProvider(
                TENANT,
                "101",
                "azure-openai",
                "Azure Production",
                "https://azure.example.com",
                "api_key",
                new ModelSecretRef("secret-77", "****suffix"),
                ModelProviderStatus.ACTIVE,
                "REQ-PROVIDER-APPROVED",
                true,
                REQUESTED_AT.minusSeconds(60),
                REQUESTED_AT.minusSeconds(30)
        );
    }

    private static ModelConfig config(
            String name,
            ModelConfigStatus status,
            String approvalRequestId,
            Map<String, Object> options
    ) {
        return new ModelConfig(
                TENANT,
                "201",
                "101",
                "gpt-enterprise",
                name,
                ModelType.CHAT,
                32768,
                null,
                new BigDecimal("0.0120"),
                new BigDecimal("0.0340"),
                status,
                approvalRequestId,
                new ModelRuntimeOptions(options),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                REQUESTED_AT.minusSeconds(60),
                REQUESTED_AT
        );
    }
}
