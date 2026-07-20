package com.aiplatform.knowledge.domain.modelpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.modelpolicy.spec.KnowledgeModelUsagePolicySpec;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeModelUsagePolicySpecTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-25T08:00:00Z"), ZoneOffset.UTC);
    private static final String POLICY_DIGEST = "a".repeat(64);

    @Test
    void allowsEmbeddingWhenProjectionMatchesKnowledgeBaseUseCase() {
        KnowledgeModelUsageDecision decision = new KnowledgeModelUsagePolicySpec(CLOCK)
                .decide(projection(true, Set.of("7001"), null), request("7001", 100));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.runtimeDescriptor().modelCode()).isEqualTo("bge-m3");
        assertThat(decision.runtimeDescriptor().connectionRef().secretRefId()).isEqualTo("secret-9001");
        assertThat(decision.auditAttributes()).containsEntry("policyVersion", "v1");
    }

    @Test
    void deniesWhenProjectionIsMissingKnowledgeBaseScope() {
        KnowledgeModelUsageDecision decision = new KnowledgeModelUsagePolicySpec(CLOCK)
                .decide(projection(true, Set.of("7002"), null), request("7001", 100));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("knowledge_base_not_allowed");
        assertThat(decision.reason()).contains("模型未授权给当前知识库");
    }

    @Test
    void deniesWhenProjectionExpired() {
        KnowledgeModelUsageDecision decision = new KnowledgeModelUsagePolicySpec(CLOCK)
                .decide(projection(true, Set.of(), Instant.parse("2026-05-25T07:59:59Z")), request("7001", 100));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("model_policy_expired");
    }

    @Test
    void rejectsEmptyRoleSnapshotInsteadOfPolicyEvaluation() {
        assertThatThrownBy(() -> request("7001", 100, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色快照不能为空");
    }

    @Test
    void rejectsBlankRoleCodeInsteadOfPolicyEvaluation() {
        assertThatThrownBy(() -> request("7001", 100, Set.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码不能为空");
    }

    @Test
    void noAuthRuntimeConnectionDoesNotCarrySecretRef() {
        KnowledgeModelRuntimeConnectionRef connectionRef = new KnowledgeModelRuntimeConnectionRef(
                "http://localhost:11434",
                "NO_AUTH",
                null,
                null
        );

        assertThat(connectionRef.authType()).isEqualTo("no_auth");
        assertThat(connectionRef.secretRefId()).isNull();
        assertThat(connectionRef.secretMaskedValue()).isNull();
    }

    @Test
    void cloudCredentialRuntimeConnectionKeepsCredentialRefWithoutPlainMaterial() {
        KnowledgeModelRuntimeConnectionRef connectionRef = new KnowledgeModelRuntimeConnectionRef(
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "AWS_CREDENTIALS",
                "9101",
                "ak-***tail"
        );

        assertThat(connectionRef.authType()).isEqualTo("aws_credentials");
        assertThat(connectionRef.secretRefId()).isEqualTo("9101");
        assertThat(connectionRef.secretMaskedValue()).isEqualTo("ak-***tail");
    }

    @Test
    void credentialRuntimeConnectionRejectsMissingMaskedSecretInsteadOfPlaceholder() {
        assertThatThrownBy(() -> new KnowledgeModelRuntimeConnectionRef(
                "https://api.siliconflow.cn/v1",
                "api_key",
                "9001",
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型供应商凭证材料脱敏值不能为空");
    }

    @Test
    void noAuthRuntimeConnectionRejectsSecretRef() {
        assertThatThrownBy(() -> new KnowledgeModelRuntimeConnectionRef(
                "http://localhost:11434",
                "no_auth",
                "9001",
                "******"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_auth 模型供应商不允许携带凭证材料引用");
    }

    @Test
    void normalizesProjectionFactsBeforeRuntimeUse() {
        KnowledgeModelUsagePolicyProjection projection = new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                " 4001 ",
                " 3001 ",
                " silicon-flow ",
                " openai-compatible ",
                runtimeConnection(),
                " bge-m3 ",
                7,
                " embedding ",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                Set.of(" 7001 "),
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                1000,
                0,
                " v1 ",
                1L,
                POLICY_DIGEST,
                null,
                true,
                " event-001 ",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        );

        assertThat(projection.modelConfigId()).isEqualTo("4001");
        assertThat(projection.providerCode()).isEqualTo("silicon-flow");
        assertThat(projection.providerKind()).isEqualTo("openai-compatible");
        assertThat(projection.modelCode()).isEqualTo("bge-m3");
        assertThat(projection.allowedKnowledgeBaseIds()).containsExactly("7001");
        assertThat(projection.sourceEventId()).isEqualTo("event-001");
        assertThat(projection.allowsKnowledgeBase(" 7001 ")).isTrue();
        assertThat(projection.runtimeDescriptor().policyVersion()).isEqualTo("v1");
    }

    @Test
    void rejectsBlankKnowledgeBaseScopeInsteadOfGrantingInvalidPolicyFact() {
        assertThatThrownBy(() -> new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                Set.of(" "),
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                1000,
                0,
                "v1",
                1L,
                POLICY_DIGEST,
                null,
                true,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库 ID不能为空");
    }

    @Test
    void rejectsProjectionWithoutCapabilitiesInsteadOfPolicyDenial() {
        assertThatThrownBy(() -> new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "bge-m3",
                7,
                "EMBEDDING",
                Set.of(),
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                Set.of("7001"),
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                1000,
                0,
                "v1",
                1L,
                POLICY_DIGEST,
                null,
                true,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型能力不能为空");
    }

    @Test
    void rejectsProjectionWithoutAllowedUseCasesInsteadOfPolicyDenial() {
        assertThatThrownBy(() -> new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(),
                Set.of("7001"),
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                1000,
                0,
                "v1",
                1L,
                POLICY_DIGEST,
                null,
                true,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("允许使用场景不能为空");
    }

    @Test
    void rejectsDamagedProjectionCollectionsInsteadOfJdkNullPointerException() {
        Set<KnowledgeModelCapability> capabilities = new LinkedHashSet<>();
        capabilities.add(null);
        assertThatThrownBy(() -> new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "bge-m3",
                7,
                "EMBEDDING",
                capabilities,
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                Set.of("7001"),
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                1000,
                0,
                "v1",
                1L,
                POLICY_DIGEST,
                null,
                true,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型能力不能为空");

        Map<String, Object> effectiveOptions = new LinkedHashMap<>();
        effectiveOptions.put("dimensions", null);
        assertThatThrownBy(() -> new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                Set.of("7001"),
                effectiveOptions,
                POLICY_DIGEST,
                1000,
                0,
                "v1",
                1L,
                POLICY_DIGEST,
                null,
                true,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
    }

    private static KnowledgeModelUsagePolicyProjection projection(
            boolean enabled,
            Set<String> allowedKnowledgeBaseIds,
            Instant expiresAt
    ) {
        return new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                allowedKnowledgeBaseIds,
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                1000,
                0,
                "v1",
                1L,
                POLICY_DIGEST,
                expiresAt,
                enabled,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }

    private static KnowledgeModelRuntimeConnectionRef runtimeConnection() {
        return new KnowledgeModelRuntimeConnectionRef(
                "https://api.siliconflow.cn/v1",
                "api_key",
                "secret-9001",
                "******"
        );
    }

    private static KnowledgeModelUsageRequest request(String knowledgeBaseId, long estimatedInputTokens) {
        return request(knowledgeBaseId, estimatedInputTokens, Set.of("admin"));
    }

    private static KnowledgeModelUsageRequest request(
            String knowledgeBaseId,
            long estimatedInputTokens,
            Set<String> roleCodes
    ) {
        return new KnowledgeModelUsageRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                roleCodes,
                Set.of(),
                knowledgeBaseId,
                KnowledgeModelUseCase.RAG_INDEXING,
                KnowledgeModelCapability.EMBEDDING,
                "4001",
                Map.of(),
                estimatedInputTokens,
                0
        );
    }
}
