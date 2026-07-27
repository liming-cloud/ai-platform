package com.aiplatform.foundation.modelruntime.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelDeploymentTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final ProviderConnectionId CONNECTION_ID =
            new ProviderConnectionId(UUID.fromString("41000000-0000-0000-0000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-07-27T04:00:00Z");

    @Test
    void 已验证对话部署才能发布并冻结其供应商修订() {
        var deployment = ModelDeployment.create(
                TENANT_ID,
                new ModelDeploymentId(UUID.fromString("42000000-0000-0000-0000-000000000001")),
                CONNECTION_ID,
                3,
                "gpt-compatible",
                "客服对话模型",
                ModelType.CHAT,
                Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT),
                32_768,
                2_048,
                null,
                NOW);

        var published = deployment.verify().publish();

        assertEquals(ModelDeploymentStatus.PUBLISHED, published.status());
        assertEquals(2, published.version());
        assertEquals(3, published.providerConnectionRevision());
    }

    @Test
    void 草稿部署不得直接发布() {
        var deployment = validEmbeddingDeployment();

        assertThrows(ModelDeploymentStateException.class, deployment::publish);
    }

    @Test
    void 嵌入部署必须声明嵌入能力和正维度() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelDeployment.create(
                        TENANT_ID,
                        new ModelDeploymentId(
                                UUID.fromString("42000000-0000-0000-0000-000000000002")),
                        CONNECTION_ID,
                        1,
                        "embedding-model",
                        "向量模型",
                        ModelType.EMBEDDING,
                        Set.of(ModelCapability.CHAT),
                        8_192,
                        null,
                        0,
                        NOW));
    }

    @Test
    void 对话部署不得混入嵌入维度() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelDeployment.create(
                        TENANT_ID,
                        new ModelDeploymentId(
                                UUID.fromString("42000000-0000-0000-0000-000000000003")),
                        CONNECTION_ID,
                        1,
                        "chat-model",
                        "对话模型",
                        ModelType.CHAT,
                        Set.of(ModelCapability.CHAT),
                        8_192,
                        1_024,
                        1_536,
                        NOW));
    }

    private static ModelDeployment validEmbeddingDeployment() {
        return ModelDeployment.create(
                TENANT_ID,
                new ModelDeploymentId(UUID.fromString("42000000-0000-0000-0000-000000000004")),
                CONNECTION_ID,
                1,
                "embedding-model",
                "向量模型",
                ModelType.EMBEDDING,
                Set.of(ModelCapability.EMBEDDING),
                8_192,
                null,
                1_536,
                NOW);
    }
}
