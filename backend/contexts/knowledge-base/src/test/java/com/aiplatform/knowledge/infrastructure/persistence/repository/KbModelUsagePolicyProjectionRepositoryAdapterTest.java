package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.knowledge.infrastructure.persistence.converter.KbModelUsagePolicyProjectionPersistenceConverter;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbModelUsagePolicyProjectionPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbModelUsagePolicyProjectionMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KbModelUsagePolicyProjectionRepositoryAdapterTest {
    private static final String POLICY_DIGEST = "a".repeat(64);

    @Test
    void saveInsertsWhenNoActiveProjectionExists() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionRepositoryAdapter repository = repository(mapper);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbModelUsagePolicyProjectionPersistenceEntity.class))).thenReturn(1);

        repository.save(projection());

        verify(mapper).insert(any(KbModelUsagePolicyProjectionPersistenceEntity.class));
        verify(mapper, never()).updateById(any(KbModelUsagePolicyProjectionPersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenProjectionInsertAffectsNoRows() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionRepositoryAdapter repository = repository(mapper);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbModelUsagePolicyProjectionPersistenceEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.save(projection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影保存失败：数据库未写入记录");
    }

    @Test
    void saveUpdatesOnlyTenantScopedActiveProjection() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionRepositoryAdapter repository = repository(mapper);
        when(mapper.selectOne(anyQuery())).thenReturn(row(9001L, Instant.parse("2026-05-25T08:00:00Z")));
        when(mapper.update(any(KbModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate())).thenReturn(1);

        repository.save(projection());

        ArgumentCaptor<KbModelUsagePolicyProjectionPersistenceEntity> entityCaptor =
                ArgumentCaptor.forClass(KbModelUsagePolicyProjectionPersistenceEntity.class);
        verify(mapper).update(entityCaptor.capture(), anyUpdate());
        assertThat(entityCaptor.getValue().getCreatedAt()).isEqualTo(Instant.parse("2026-05-25T08:00:00Z"));
        assertThat(entityCaptor.getValue().getVersion()).isEqualTo(1);
        verify(mapper, never()).updateById(any(KbModelUsagePolicyProjectionPersistenceEntity.class));
    }

    @Test
    void staleEventCannotOverwriteCurrentProjection() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionPersistenceEntity current = row(
                9001L,
                Instant.parse("2026-05-25T08:10:00Z")
        );
        current.setSourceEventId("event-new");
        current.setPolicyRevision(3L);
        when(mapper.selectOne(anyQuery())).thenReturn(current);

        KnowledgeModelUsagePolicyProjection saved = repository(mapper).save(projection(
                "event-old",
                Instant.parse("2026-05-25T08:05:00Z")
        ));

        assertThat(saved.sourceEventId()).isEqualTo("event-new");
        verify(mapper, never()).insert(any(KbModelUsagePolicyProjectionPersistenceEntity.class));
        verify(mapper, never()).update(any(KbModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate());
    }

    @Test
    void concurrentNewerProjectionWinsWithoutRetryingOlderPayload() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionPersistenceEntity existing = row(
                9001L,
                Instant.parse("2026-05-25T08:01:00Z")
        );
        KbModelUsagePolicyProjectionPersistenceEntity winner = row(
                9001L,
                Instant.parse("2026-05-25T08:11:00Z")
        );
        winner.setSourceEventId("event-winner");
        winner.setPolicyRevision(3L);
        winner.setVersion(1);
        when(mapper.selectOne(anyQuery())).thenReturn(existing, winner);
        when(mapper.update(any(KbModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate())).thenReturn(0);

        KnowledgeModelUsagePolicyProjection saved = repository(mapper).save(projection(
                "event-loser",
                Instant.parse("2026-05-25T08:10:00Z")
        ));

        assertThat(saved.sourceEventId()).isEqualTo("event-winner");
    }

    @Test
    void saveFailsClosedWhenTenantScopedProjectionUpdateIsLost() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionRepositoryAdapter repository = repository(mapper);
        when(mapper.selectOne(anyQuery())).thenReturn(row(9001L, Instant.parse("2026-05-25T08:00:00Z")));
        when(mapper.update(any(KbModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate())).thenReturn(0);

        assertThatThrownBy(() -> repository.save(projection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影保存失败");
        verify(mapper, never()).updateById(any(KbModelUsagePolicyProjectionPersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenExistingProjectionIdMissing() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionRepositoryAdapter repository = repository(mapper);
        when(mapper.selectOne(anyQuery())).thenReturn(row(null, Instant.parse("2026-05-25T08:00:00Z")));

        assertThatThrownBy(() -> repository.save(projection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型策略投影 ID不能为空");
        verify(mapper, never()).updateById(any(KbModelUsagePolicyProjectionPersistenceEntity.class));
    }

    @Test
    void equalRevisionWithDifferentDigestFailsClosedInsteadOfTrustingTimestamp() {
        KbModelUsagePolicyProjectionMapper mapper = mock(KbModelUsagePolicyProjectionMapper.class);
        KbModelUsagePolicyProjectionPersistenceEntity existing = row(
                9001L,
                Instant.parse("2026-05-25T08:10:00Z")
        );
        existing.setPolicyRevision(2L);
        existing.setPolicyDigest("b".repeat(64));
        when(mapper.selectOne(anyQuery())).thenReturn(existing);

        assertThatThrownBy(() -> repository(mapper).save(projection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("同一修订号载荷摘要冲突");
        verify(mapper, never()).update(any(KbModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate());
    }

    private static KbModelUsagePolicyProjectionRepositoryAdapter repository(
            KbModelUsagePolicyProjectionMapper mapper
    ) {
        return new KbModelUsagePolicyProjectionRepositoryAdapter(
                mapper,
                new KbModelUsagePolicyProjectionPersistenceConverter(new JsonMapper())
        );
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<KbModelUsagePolicyProjectionPersistenceEntity> anyQuery() {
        return any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<KbModelUsagePolicyProjectionPersistenceEntity> anyUpdate() {
        return any(Wrapper.class);
    }

    private static KnowledgeModelUsagePolicyProjection projection() {
        return projection("event-002", Instant.parse("2026-05-25T08:05:00Z"));
    }

    private static KnowledgeModelUsagePolicyProjection projection(String eventId, Instant lastEventAt) {
        return new KnowledgeModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.siliconflow.cn/v1",
                        "api_key",
                        "9001",
                        "sk-***"
                ),
                "BAAI/bge-m3",
                7,
                "EMBEDDING",
                Set.of(KnowledgeModelCapability.EMBEDDING),
                Set.of(KnowledgeModelUseCase.RAG_INDEXING, KnowledgeModelUseCase.RAG_QUERY),
                Set.of(),
                Map.of("dimensions", 1536),
                POLICY_DIGEST,
                8192,
                0,
                "v1",
                2L,
                POLICY_DIGEST,
                null,
                true,
                eventId,
                2L,
                lastEventAt
        );
    }

    private static KbModelUsagePolicyProjectionPersistenceEntity row(Long id, Instant lastEventAt) {
        KbModelUsagePolicyProjectionPersistenceEntity row = new KbModelUsagePolicyProjectionPersistenceEntity();
        row.setId(id);
        row.setTenantId(2001L);
        row.setModelConfigId("4001");
        row.setProviderId(3001L);
        row.setProviderCode("silicon-flow");
        row.setProviderKind("openai-compatible");
        row.setProviderEndpoint("https://api.siliconflow.cn/v1");
        row.setAuthType("api_key");
        row.setSecretRefId("9001");
        row.setSecretMaskedValue("sk-***");
        row.setModelCode("BAAI/bge-m3");
        row.setModelConfigVersion(7);
        row.setModelType("EMBEDDING");
        row.setCapabilities("EMBEDDING");
        row.setAllowedUseCases("RAG_INDEXING,RAG_QUERY");
        row.setAllowedKnowledgeBaseIds("");
        row.setEffectiveOptionsJson("{\"dimensions\":1536}");
        row.setEffectiveOptionsDigest(POLICY_DIGEST);
        row.setMaxInputTokens(8192);
        row.setMaxOutputTokens(0);
        row.setPolicyVersion("v1");
        row.setPolicyRevision(1L);
        row.setPolicyDigest(POLICY_DIGEST);
        row.setEnabled(true);
        row.setSourceEventId("event-001");
        row.setSourceEventVersion(1L);
        row.setLastEventAt(lastEventAt);
        row.setCreatedAt(Instant.parse("2026-05-25T08:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-05-25T08:00:00Z"));
        row.setVersion(0);
        return row;
    }
}
