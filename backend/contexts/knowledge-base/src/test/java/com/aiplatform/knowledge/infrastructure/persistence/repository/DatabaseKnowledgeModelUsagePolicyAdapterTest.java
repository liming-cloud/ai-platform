package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyProjectionRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatabaseKnowledgeModelUsagePolicyAdapterTest {
    private static final String POLICY_DIGEST = "a".repeat(64);

    @Test
    void deniesWhenLocalProjectionIsMissing() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        when(repository.findByModelConfigId(TenantId.of("2001"), "4001")).thenReturn(Optional.empty());
        DatabaseKnowledgeModelUsagePolicyAdapter adapter = new DatabaseKnowledgeModelUsagePolicyAdapter(
                repository,
                Clock.systemUTC()
        );

        KnowledgeModelUsageDecision decision = adapter.decide(request());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("model_policy_projection_missing");
    }

    @Test
    void allowsWhenLocalProjectionIsEnabled() {
        KnowledgeModelUsagePolicyProjectionRepository repository = mock(KnowledgeModelUsagePolicyProjectionRepository.class);
        when(repository.findByModelConfigId(TenantId.of("2001"), "4001")).thenReturn(Optional.of(projection()));
        DatabaseKnowledgeModelUsagePolicyAdapter adapter = new DatabaseKnowledgeModelUsagePolicyAdapter(
                repository,
                Clock.fixed(Instant.parse("2026-05-25T08:00:00Z"), ZoneOffset.UTC)
        );

        KnowledgeModelUsageDecision decision = adapter.decide(request());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.runtimeDescriptor().providerCode()).isEqualTo("silicon-flow");
        assertThat(decision.runtimeDescriptor().connectionRef().secretRefId()).isEqualTo("secret-9001");
    }

    private static KnowledgeModelUsageRequest request() {
        return new KnowledgeModelUsageRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                Set.of("admin"),
                Set.of(),
                "7001",
                KnowledgeModelUseCase.RAG_QUERY,
                KnowledgeModelCapability.EMBEDDING,
                "4001",
                Map.of(),
                20,
                0
        );
    }

    private static KnowledgeModelUsagePolicyProjection projection() {
        return new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                runtimeConnection(),
                "BAAI/bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(KnowledgeModelUseCase.RAG_QUERY),
                Set.of(),
                Map.of(),
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
}
