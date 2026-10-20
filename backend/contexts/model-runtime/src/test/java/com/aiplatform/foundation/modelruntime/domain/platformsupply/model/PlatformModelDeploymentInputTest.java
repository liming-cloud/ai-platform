package com.aiplatform.foundation.modelruntime.domain.platformsupply.model;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformModelDeploymentInputTest {
    @Test
    void chatOutputCannotExceedDeclaredContext() {
        assertThrows(IllegalArgumentException.class, () -> PlatformModelDeployment.create(
                new PlatformModelDeploymentId(1), new PlatformProviderConnectionId(2), 1,
                "synthetic-model", "合成部署", ModelType.CHAT, Set.of(ModelCapability.CHAT),
                128, 129, null, Instant.parse("2026-10-20T00:00:00Z")));
    }

    @Test
    void rerankCannotFallThroughToEmbeddingConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> PlatformModelDeployment.create(
                new PlatformModelDeploymentId(1), new PlatformProviderConnectionId(2), 1,
                "synthetic-model", "合成部署", ModelType.RERANK, Set.of(ModelCapability.EMBEDDING),
                128, null, 8, Instant.parse("2026-10-20T00:00:00Z")));
    }

    @Test
    void rerankDraftRequiresItsOwnPositiveLimitAndKeepsImmutablePlannedTenants() {
        var tenants = new java.util.HashSet<>(Set.of(201L));
        var inputs = inputs(tenants, 20, "合成厂商资料 v1");
        var draft = PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(1), new PlatformProviderConnectionId(2), 1,
                "synthetic-rerank", "合成重排部署", ModelType.RERANK, Set.of(ModelCapability.RERANK),
                128, null, null, Instant.parse("2026-10-20T00:00:00Z"), inputs);
        tenants.clear();
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(201L), draft.inputs().plannedTenantIds());
        org.junit.jupiter.api.Assertions.assertEquals(20, draft.inputs().rerankCandidateLimit());
        assertThrows(UnsupportedOperationException.class, () -> draft.inputs().plannedTenantIds().clear());
        assertThrows(IllegalArgumentException.class, () -> inputs(Set.of(201L), 0, "合成资料"));
        assertThrows(IllegalArgumentException.class, () -> inputs(Set.of(), 20, "合成资料"));
        assertThrows(IllegalArgumentException.class, () -> inputs(Set.of(201L), 20, "来源\n换行"));
        assertThrows(IllegalArgumentException.class, () -> PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(1), new PlatformProviderConnectionId(2), 1,
                "synthetic-rerank", "合成重排部署", ModelType.RERANK, Set.of(ModelCapability.RERANK),
                128, null, 8, Instant.parse("2026-10-20T00:00:00Z"), inputs));
    }


    @Test
    void 历史来源没有独立名称时保留缺失而不是编码或展示提示() {
        var input = new PlatformModelDeploymentInputs("合成资料", "合成区域", null, Set.of(201L), 301L, null,
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER,
                101L, Instant.parse("2026-10-20T00:00:00Z"));
        org.junit.jupiter.api.Assertions.assertNull(input.candidateDisplayName());
    }

    private static PlatformModelDeploymentInputs inputs(Set<Long> tenants, Integer limit, String source) {
        return new PlatformModelDeploymentInputs(source, "合成区域", limit, tenants, 301L, "合成重排模型",
                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.CONFIRMED_BY_ADMIN,
                101L, Instant.parse("2026-10-20T00:00:00Z"));
    }
}
