package com.aiplatform.modelhub.domain.modelpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.modelhub.domain.ModelCapability;
import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUsagePolicyDecision;
import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUsagePolicyRequest;
import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUseCase;
import com.aiplatform.modelhub.domain.modelpolicy.spec.ModelUsagePolicySpec;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelUsagePolicySpecTest {
    @Test
    void allowsWhenUseCaseCapabilityAndBudgetMatch() {
        ModelUsagePolicy policy = policy(true, Set.of(ModelUseCase.RAG_QUERY), Set.of(ModelCapability.EMBEDDING), 1000, 1000);
        ModelUsagePolicyRequest request = request(ModelUseCase.RAG_QUERY, ModelCapability.EMBEDDING, 100, 50);

        ModelUsagePolicyDecision decision = new ModelUsagePolicySpec()
                .decide(policy, request, Instant.parse("2026-05-25T08:00:00Z"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.decisionCode()).isEqualTo("allowed");
        assertThat(decision.policyVersion()).isEqualTo("v1");
    }

    @Test
    void deniesWhenPolicyIsDisabled() {
        ModelUsagePolicy policy = policy(false, Set.of(ModelUseCase.RAG_QUERY), Set.of(ModelCapability.EMBEDDING), 1000, 1000);
        ModelUsagePolicyRequest request = request(ModelUseCase.RAG_QUERY, ModelCapability.EMBEDDING, 100, 50);

        ModelUsagePolicyDecision decision = new ModelUsagePolicySpec()
                .decide(policy, request, Instant.parse("2026-05-25T08:00:00Z"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("model_policy_disabled");
        assertThat(decision.reason()).isEqualTo("模型使用策略已停用");
    }

    @Test
    void rejectsEmptyRoleSnapshotInsteadOfPolicyEvaluation() {
        assertThatThrownBy(() -> request(Set.of(), ModelUseCase.RAG_QUERY, ModelCapability.EMBEDDING, 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色快照不能为空");
    }

    @Test
    void rejectsBlankRoleCodeInsteadOfPolicyEvaluation() {
        assertThatThrownBy(() -> request(Set.of(" "), ModelUseCase.RAG_QUERY, ModelCapability.EMBEDDING, 100, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码不能为空");
    }

    @Test
    void rejectsDamagedRequestCollectionsInsteadOfJdkNullPointerException() {
        Set<PermissionCode> permissions = new LinkedHashSet<>();
        permissions.add(null);
        assertThatThrownBy(() -> request(
                Set.of("admin"),
                permissions,
                Map.of(),
                ModelUseCase.RAG_QUERY,
                ModelCapability.EMBEDDING,
                100,
                50
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限快照不能为空");

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", null);
        assertThatThrownBy(() -> request(
                Set.of("admin"),
                Set.of(new PermissionCode("model:usage:query")),
                options,
                ModelUseCase.RAG_QUERY,
                ModelCapability.EMBEDDING,
                100,
                50
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
    }

    @Test
    void normalizesPolicyFactsBeforeDecisionUse() {
        ModelUsagePolicy policy = new ModelUsagePolicy(
                new TenantId("2001"),
                " policy-001 ",
                " model-001 ",
                Set.of(ModelUseCase.RAG_QUERY),
                Set.of(ModelCapability.EMBEDDING),
                Set.of(" app-001 "),
                1000,
                1000,
                " v1 ",
                null,
                true
        );

        ModelUsagePolicyDecision decision = new ModelUsagePolicySpec()
                .decide(policy, request(ModelUseCase.RAG_QUERY, ModelCapability.EMBEDDING, 100, 50),
                        Instant.parse("2026-05-25T08:00:00Z"));

        assertThat(policy.id()).isEqualTo("policy-001");
        assertThat(policy.modelConfigId()).isEqualTo("model-001");
        assertThat(policy.allowedApplicationIds()).containsExactly("app-001");
        assertThat(decision.policyVersion()).isEqualTo("v1");
    }

    @Test
    void rejectsBlankApplicationScopeInsteadOfIgnoringInvalidPolicyFact() {
        assertThatThrownBy(() -> new ModelUsagePolicy(
                new TenantId("2001"),
                "policy-001",
                "model-001",
                Set.of(ModelUseCase.RAG_QUERY),
                Set.of(ModelCapability.EMBEDDING),
                Set.of(" "),
                1000,
                1000,
                "v1",
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("允许应用 ID不能为空");
    }

    @Test
    void rejectsDamagedPolicyCollectionsInsteadOfJdkNullPointerException() {
        Set<ModelUseCase> useCases = new LinkedHashSet<>();
        useCases.add(null);
        assertThatThrownBy(() -> new ModelUsagePolicy(
                new TenantId("2001"),
                "policy-001",
                "model-001",
                useCases,
                Set.of(ModelCapability.EMBEDDING),
                Set.of("app-001"),
                1000,
                1000,
                "v1",
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("允许用例不能为空");

        Set<ModelCapability> capabilities = new LinkedHashSet<>();
        capabilities.add(null);
        assertThatThrownBy(() -> new ModelUsagePolicy(
                new TenantId("2001"),
                "policy-001",
                "model-001",
                Set.of(ModelUseCase.RAG_QUERY),
                capabilities,
                Set.of("app-001"),
                1000,
                1000,
                "v1",
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("允许能力不能为空");
    }

    private static ModelUsagePolicy policy(
            boolean enabled,
            Set<ModelUseCase> useCases,
            Set<ModelCapability> capabilities,
            int maxInputTokens,
            int maxOutputTokens
    ) {
        return new ModelUsagePolicy(
                new TenantId("2001"),
                "policy-001",
                "model-001",
                useCases,
                capabilities,
                Set.of("app-001"),
                maxInputTokens,
                maxOutputTokens,
                "v1",
                null,
                enabled
        );
    }

    private static ModelUsagePolicyRequest request(
            ModelUseCase useCase,
            ModelCapability capability,
            long inputTokens,
            long outputTokens
    ) {
        return request(Set.of("admin"), useCase, capability, inputTokens, outputTokens);
    }

    private static ModelUsagePolicyRequest request(
            Set<String> roleCodes,
            ModelUseCase useCase,
            ModelCapability capability,
            long inputTokens,
            long outputTokens
    ) {
        return request(roleCodes, Set.of(), Map.of(), useCase, capability, inputTokens, outputTokens);
    }

    private static ModelUsagePolicyRequest request(
            Set<String> roleCodes,
            Set<PermissionCode> permissionSnapshot,
            Map<String, Object> requestedOptions,
            ModelUseCase useCase,
            ModelCapability capability,
            long inputTokens,
            long outputTokens
    ) {
        return new ModelUsagePolicyRequest(
                new TenantId("2001"),
                "1001",
                "user",
                roleCodes,
                permissionSnapshot,
                "app-001",
                useCase,
                capability,
                "model-001",
                requestedOptions,
                inputTokens,
                outputTokens
        );
    }
}
