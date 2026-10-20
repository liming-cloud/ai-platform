package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelRuntimeCapabilities;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformSupplyRerankClassificationTest {
    @Test
    void 管理员确认重排候选时不得默认为向量模型() {
        var type = assertDoesNotThrow(() -> ModelType.valueOf("RERANK"));
        var candidate = PlatformDiscoveredModelCandidate.confirmedByAdministrator("synthetic-rerank", type);

        assertEquals(type, candidate.modelType());
        assertEquals(Set.of(ModelCapability.valueOf("RERANK")), candidate.capabilities());
        assertFalse(candidate.capabilities().contains(ModelCapability.EMBEDDING));
    }

    @Test
    void 公开扩展候选与领域的重排分类保持一致() {
        var type = assertDoesNotThrow(() ->
                com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.valueOf("RERANK"));
        var candidate = com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate
                .confirmedByAdministrator("synthetic-rerank", type);

        assertEquals(type, candidate.modelType());
        assertEquals(Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.valueOf("RERANK")),
                candidate.capabilities());
        assertFalse(candidate.capabilities().contains(
                com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.EMBEDDING));
    }

    @Test
    void 重排候选不得混入向量或对话能力() {
        for (var extra : Set.of(ModelCapability.EMBEDDING, ModelCapability.CHAT)) {
            assertThrows(IllegalArgumentException.class, () -> PlatformDiscoveredModelCandidate.confirmedByProvider(
                    "synthetic-rerank", ModelType.RERANK, Set.of(ModelCapability.RERANK, extra)));
        }
    }

    @Test
    void 公开扩展重排候选不得混入向量能力() {
        assertThrows(IllegalArgumentException.class, () ->
                com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformDiscoveredModelCandidate
                        .confirmedByProvider("synthetic-rerank",
                                com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.RERANK,
                                Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.RERANK,
                                        com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.EMBEDDING)));
    }

    @Test
    void 租户旧部署不得把重排类型默认为向量运行() {
        assertThrows(IllegalArgumentException.class, () -> ModelDeployment.create(new TenantId(100000000000000001L),
                new ModelDeploymentId(320000000000000001L), new ProviderConnectionId(310000000000000001L), 1L,
                "synthetic-rerank", "重排反例", ModelType.RERANK, Set.of(ModelCapability.EMBEDDING),
                8192, null, 1024, Instant.parse("2026-10-20T00:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> ModelRuntimeCapabilities.embedding(8192, 1024)
                .requireFor(ModelType.RERANK));
    }

    @Test
    void 租户旧发现契约不能因平台新增类型而接受重排候选() {
        assertThrows(IllegalArgumentException.class, () -> new com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel(
                "synthetic-rerank", ModelType.RERANK, Set.of(ModelCapability.RERANK)));
        assertThrows(IllegalArgumentException.class, () -> new com.aiplatform.foundation.modelruntime.application.extension.facade.DiscoveredModel(
                "synthetic-rerank", com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.RERANK,
                Set.of(com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability.RERANK)));
    }
}
