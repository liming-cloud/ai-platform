package com.aiplatform.modelhub.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestClaim;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResourceType;
import com.aiplatform.modelhub.application.idempotency.ModelCreationResultSnapshot;
import com.aiplatform.modelhub.infrastructure.persistence.entity.ModelCreationRequestPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelCreationRequestMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelCreationRequestRepositoryAdapterTest {
    private static final String DIGEST = "a".repeat(64);

    @Test
    void successfulInsertOwnsTheCreateRequest() {
        ModelCreationRequestMapper mapper = mock(ModelCreationRequestMapper.class);
        when(mapper.insertClaim(any())).thenReturn(91L);
        ModelCreationRequestRepositoryAdapter adapter = new ModelCreationRequestRepositoryAdapter(mapper);

        ModelCreationRequestClaim claim = adapter.claim(
                TenantId.of("2001"),
                ModelCreationResourceType.MODEL_PROVIDER,
                "provider-create-001",
                DIGEST
        );

        assertThat(claim.acquired()).isTrue();
        assertThat(claim.requestDigest()).isEqualTo(DIGEST);
        ArgumentCaptor<ModelCreationRequestPersistenceEntity> captor =
                ArgumentCaptor.forClass(ModelCreationRequestPersistenceEntity.class);
        verify(mapper).insertClaim(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(captor.getValue().getResourceType()).isEqualTo("MODEL_PROVIDER");
        assertThat(captor.getValue().getRequestId()).isEqualTo("provider-create-001");
    }

    @Test
    void conflictReplaysOnlyACompletedResourceAndPreservesTheStoredDigest() {
        ModelCreationRequestMapper mapper = mock(ModelCreationRequestMapper.class);
        when(mapper.insertClaim(any())).thenReturn(null);
        ModelCreationRequestPersistenceEntity existing = completed("b".repeat(64), 9001L);
        when(mapper.findClaim(2001L, "MODEL_PROVIDER", "provider-create-001")).thenReturn(existing);
        ModelCreationRequestRepositoryAdapter adapter = new ModelCreationRequestRepositoryAdapter(mapper);

        ModelCreationRequestClaim claim = adapter.claim(
                TenantId.of("2001"),
                ModelCreationResourceType.MODEL_PROVIDER,
                "provider-create-001",
                DIGEST
        );

        assertThat(claim.acquired()).isFalse();
        assertThat(claim.requestDigest()).isEqualTo("b".repeat(64));
        assertThat(claim.result()).isEqualTo(new ModelCreationResultSnapshot(
                ModelCreationResourceType.MODEL_PROVIDER,
                null,
                "9001",
                null,
                null,
                "PENDING_APPROVAL",
                "REQ-9001",
                null
        ));
    }

    @Test
    void incompleteCommittedClaimAndUnfencedCompletionFailClosed() {
        ModelCreationRequestMapper mapper = mock(ModelCreationRequestMapper.class);
        when(mapper.insertClaim(any())).thenReturn(null);
        when(mapper.findClaim(2001L, "MODEL_CONFIG", "model-create-001"))
                .thenReturn(new ModelCreationRequestPersistenceEntity());
        ModelCreationRequestRepositoryAdapter adapter = new ModelCreationRequestRepositoryAdapter(mapper);

        assertThatThrownBy(() -> adapter.claim(
                TenantId.of("2001"),
                ModelCreationResourceType.MODEL_CONFIG,
                "model-create-001",
                DIGEST
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未完成");

        when(mapper.completeClaim(any())).thenReturn(0);
        assertThatThrownBy(() -> adapter.complete(
                TenantId.of("2001"),
                ModelCreationResourceType.MODEL_CONFIG,
                "model-create-001",
                DIGEST,
                new ModelCreationResultSnapshot(
                        ModelCreationResourceType.MODEL_CONFIG,
                        "9001",
                        "9101",
                        "gpt-4.1-mini",
                        "CHAT",
                        "PENDING_APPROVAL",
                        "REQ-9101",
                        "NOT_TESTED"
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未命中");
    }

    @Test
    void completionPersistsEveryPublicModelResponseFieldWithoutPayloadOrSecretMaterial() {
        ModelCreationRequestMapper mapper = mock(ModelCreationRequestMapper.class);
        when(mapper.completeClaim(any())).thenReturn(1);
        ModelCreationRequestRepositoryAdapter adapter = new ModelCreationRequestRepositoryAdapter(mapper);
        ModelCreationResultSnapshot snapshot = new ModelCreationResultSnapshot(
                ModelCreationResourceType.MODEL_CONFIG,
                "9001",
                "9101",
                "gpt-4.1-mini",
                "CHAT",
                "INACTIVE",
                null,
                "NOT_TESTED"
        );

        adapter.complete(
                TenantId.of("2001"),
                ModelCreationResourceType.MODEL_CONFIG,
                "model-create-001",
                DIGEST,
                snapshot
        );

        ArgumentCaptor<ModelCreationRequestPersistenceEntity> captor =
                ArgumentCaptor.forClass(ModelCreationRequestPersistenceEntity.class);
        verify(mapper).completeClaim(captor.capture());
        ModelCreationRequestPersistenceEntity saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(2001L);
        assertThat(saved.getResourceType()).isEqualTo("MODEL_CONFIG");
        assertThat(saved.getRequestId()).isEqualTo("model-create-001");
        assertThat(saved.getRequestDigest()).isEqualTo(DIGEST);
        assertThat(saved.getResultParentResourceId()).isEqualTo(9001L);
        assertThat(saved.getResultResourceId()).isEqualTo(9101L);
        assertThat(saved.getResultResourceCode()).isEqualTo("gpt-4.1-mini");
        assertThat(saved.getResultModelType()).isEqualTo("CHAT");
        assertThat(saved.getResultStatus()).isEqualTo("INACTIVE");
        assertThat(saved.getResultApprovalRequestId()).isNull();
        assertThat(saved.getResultConnectivityStatus()).isEqualTo("NOT_TESTED");
    }

    @Test
    void completedModelClaimReplaysAnExactSnapshotEvenWhenApprovalIsNotRequired() {
        ModelCreationRequestMapper mapper = mock(ModelCreationRequestMapper.class);
        when(mapper.insertClaim(any())).thenReturn(null);
        ModelCreationRequestPersistenceEntity existing = new ModelCreationRequestPersistenceEntity();
        existing.setResourceType("MODEL_CONFIG");
        existing.setRequestDigest("b".repeat(64));
        existing.setResultParentResourceId(9001L);
        existing.setResultResourceId(9101L);
        existing.setResultResourceCode("gpt-4.1-mini");
        existing.setResultModelType("CHAT");
        existing.setResultStatus("INACTIVE");
        existing.setResultConnectivityStatus("NOT_TESTED");
        existing.setCompletedAt(Instant.parse("2026-07-21T03:00:00Z"));
        when(mapper.findClaim(2001L, "MODEL_CONFIG", "model-create-001")).thenReturn(existing);
        ModelCreationRequestRepositoryAdapter adapter = new ModelCreationRequestRepositoryAdapter(mapper);

        ModelCreationRequestClaim claim = adapter.claim(
                TenantId.of("2001"),
                ModelCreationResourceType.MODEL_CONFIG,
                "model-create-001",
                DIGEST
        );

        assertThat(claim.result().toModelConfigResult().approvalRequestId()).isNull();
        assertThat(claim.result().toModelConfigResult().status()).isEqualTo("INACTIVE");
        assertThat(claim.result().toModelConfigResult().modelConfigId()).isEqualTo("9101");
    }

    private static ModelCreationRequestPersistenceEntity completed(String digest, long resultResourceId) {
        ModelCreationRequestPersistenceEntity existing = new ModelCreationRequestPersistenceEntity();
        existing.setResourceType("MODEL_PROVIDER");
        existing.setRequestDigest(digest);
        existing.setResultResourceId(resultResourceId);
        existing.setResultStatus("PENDING_APPROVAL");
        existing.setResultApprovalRequestId("REQ-" + resultResourceId);
        existing.setCompletedAt(Instant.parse("2026-07-21T03:00:00Z"));
        return existing;
    }
}
