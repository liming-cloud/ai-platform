package com.aiplatform.foundation.modelruntime.application.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.application.port.out.ModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.model.ModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.model.ProviderConnectionId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultPublishedEmbeddingDeploymentLookupTest {

    private static final TenantId TENANT_ID =
            new TenantId(100000000000000001L);
    private static final ModelDeploymentId DEPLOYMENT_ID = new ModelDeploymentId(
            320000000000000001L);

    @Test
    void 只投影已发布嵌入部署的冻结事实() {
        var repository = mock(ModelDeploymentRepository.class);
        when(repository.findById(TENANT_ID, DEPLOYMENT_ID))
                .thenReturn(Optional.of(published(ModelType.EMBEDDING)));
        var lookup = new DefaultPublishedEmbeddingDeploymentLookup(repository);

        var snapshot = lookup.find(TENANT_ID, DEPLOYMENT_ID.value()).orElseThrow();

        assertEquals(DEPLOYMENT_ID.value(), snapshot.deploymentId());
        assertEquals(2, snapshot.deploymentRevision());
        assertEquals(128, snapshot.dimension());
    }

    @Test
    void 对话部署不能冒充嵌入部署() {
        var repository = mock(ModelDeploymentRepository.class);
        when(repository.findById(TENANT_ID, DEPLOYMENT_ID))
                .thenReturn(Optional.of(published(ModelType.CHAT)));
        var lookup = new DefaultPublishedEmbeddingDeploymentLookup(repository);

        assertTrue(lookup.find(TENANT_ID, DEPLOYMENT_ID.value()).isEmpty());
    }

    private static ModelDeployment published(ModelType type) {
        return ModelDeployment.create(
                        TENANT_ID,
                        DEPLOYMENT_ID,
                        new ProviderConnectionId(310000000000000001L),
                        1,
                        type == ModelType.CHAT
                                ? "deterministic-chat"
                                : "deterministic-embedding",
                        "验收部署",
                        type,
                        type == ModelType.CHAT
                                ? Set.of(ModelCapability.CHAT)
                                : Set.of(ModelCapability.EMBEDDING),
                        8192,
                        type == ModelType.CHAT ? 1024 : null,
                        type == ModelType.EMBEDDING ? 128 : null,
                        Instant.parse("2026-07-27T00:00:00Z"))
                .verify()
                .publish();
    }
}
