package com.aiplatform.knowledge.domain.modelpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeModelUsageDecisionTest {
    @Test
    void rejectsDeniedDecisionWithoutReason() {
        assertThatThrownBy(() -> new KnowledgeModelUsageDecision(
                false,
                "denied",
                null,
                null,
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝原因不能为空");
    }

    @Test
    void rejectsAllowedDecisionWithoutRuntimeDescriptor() {
        assertThatThrownBy(() -> new KnowledgeModelUsageDecision(
                true,
                "allowed",
                "",
                null,
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型运行时描述不能为空");
    }

    @Test
    void rejectsDamagedUsageRequestCollectionsInsteadOfJdkNullPointerException() {
        Set<PermissionCode> permissions = new LinkedHashSet<>();
        permissions.add(null);
        assertThatThrownBy(() -> usageRequest(permissions, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限快照不能为空");

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", null);
        assertThatThrownBy(() -> usageRequest(Set.of(new PermissionCode("knowledge:document:index")), options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
    }

    @Test
    void rejectsDeniedDecisionWithRuntimeDescriptor() {
        assertThatThrownBy(() -> new KnowledgeModelUsageDecision(
                false,
                "denied",
                "没有可用知识库模型策略",
                runtimeDescriptor(),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝决策不能携带模型运行时描述");
    }

    @Test
    void normalizesDecisionFactsBeforeAuditUse() {
        KnowledgeModelUsageDecision decision = new KnowledgeModelUsageDecision(
                false,
                " denied ",
                " 没有可用知识库模型策略 ",
                null,
                Map.of(" traceId ", " trace-1 ")
        );

        assertThat(decision.decisionCode()).isEqualTo("denied");
        assertThat(decision.reason()).isEqualTo("没有可用知识库模型策略");
        assertThat(decision.auditAttributes()).containsEntry("traceId", "trace-1");
    }

    @Test
    void rejectsBlankAuditAttributeInsteadOfLeakingDamagedAuditFacts() {
        assertThatThrownBy(() -> new KnowledgeModelUsageDecision(
                false,
                "denied",
                "没有可用知识库模型策略",
                null,
                Map.of("traceId", " ")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计属性 value不能为空");
    }

    @Test
    void normalizesRuntimeDescriptorFactsBeforeSpringAiUse() {
        KnowledgeModelRuntimeDescriptor descriptor = new KnowledgeModelRuntimeDescriptor(
                " 9001 ",
                " 3001 ",
                " openai ",
                " openai-compatible ",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.test-provider.example/v1",
                        "no_auth",
                        null,
                        null
                ),
                " text-embedding-3-small ",
                7,
                " embedding ",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Map.of("dimensions", 1536),
                "a".repeat(64),
                " policy-v1 "
        );

        assertThat(descriptor.modelConfigId()).isEqualTo("9001");
        assertThat(descriptor.providerId()).isEqualTo("3001");
        assertThat(descriptor.providerCode()).isEqualTo("openai");
        assertThat(descriptor.providerKind()).isEqualTo("openai-compatible");
        assertThat(descriptor.modelCode()).isEqualTo("text-embedding-3-small");
        assertThat(descriptor.policyVersion()).isEqualTo("policy-v1");
    }

    @Test
    void rejectsRuntimeDescriptorWithoutCapabilitiesInsteadOfEmptyModelRuntime() {
        assertThatThrownBy(() -> new KnowledgeModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.test-provider.example/v1",
                        "no_auth",
                        null,
                        null
                ),
                "text-embedding-3-small",
                7,
                "EMBEDDING",
                Set.of(),
                Map.of("dimensions", 1536),
                "a".repeat(64),
                "policy-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型能力不能为空");
    }

    @Test
    void rejectsDamagedRuntimeDescriptorCollectionsInsteadOfJdkNullPointerException() {
        Set<KnowledgeModelCapability> capabilities = new LinkedHashSet<>();
        capabilities.add(null);
        assertThatThrownBy(() -> new KnowledgeModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.test-provider.example/v1",
                        "no_auth",
                        null,
                        null
                ),
                "text-embedding-3-small",
                7,
                "EMBEDDING",
                capabilities,
                Map.of("dimensions", 1536),
                "a".repeat(64),
                "policy-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型能力不能为空");

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("dimensions", null);
        assertThatThrownBy(() -> new KnowledgeModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.test-provider.example/v1",
                        "no_auth",
                        null,
                        null
                ),
                "text-embedding-3-small",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                options,
                "a".repeat(64),
                "policy-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
    }

    private static KnowledgeModelRuntimeDescriptor runtimeDescriptor() {
        return new KnowledgeModelRuntimeDescriptor(
                "9001",
                "3001",
                "openai",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.test-provider.example/v1",
                        "no_auth",
                        null,
                        null
                ),
                "text-embedding-3-small",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Map.of("dimensions", 1536),
                "a".repeat(64),
                "policy-v1"
        );
    }

    private static KnowledgeModelUsageRequest usageRequest(
            Set<PermissionCode> permissionSnapshot,
            Map<String, Object> requestedOptions
    ) {
        return new KnowledgeModelUsageRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                Set.of("admin"),
                permissionSnapshot,
                "kb-1",
                KnowledgeModelUseCase.RAG_INDEXING,
                KnowledgeModelCapability.EMBEDDING,
                "9001",
                requestedOptions,
                10,
                0
        );
    }
}
