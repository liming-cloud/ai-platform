package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbDocumentChunkPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbDocumentChunkMapper;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KbDocumentChunkRetrievalRow;
import com.aiplatform.knowledge.infrastructure.persistence.projection.KbKnowledgeIndexActiveRow;
import com.aiplatform.knowledge.domain.port.retrieval.model.RetrievalSearchQuery;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KbDocumentChunkRepositoryAdapterTest {
    @Test
    void cleansOnlyTheMapperSelectedObsoleteEmbeddingBatch() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        Instant cutoff = Instant.parse("2026-07-17T08:00:00Z");
        when(mapper.deleteObsoleteEmbeddings(cutoff, 20)).thenReturn(3);

        KbDocumentChunkRepositoryAdapter adapter = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThat(adapter.deleteObsoleteEmbeddings(cutoff, 20)).isEqualTo(3);
        verify(mapper).deleteObsoleteEmbeddings(cutoff, 20);
    }

    @Test
    void nextChunkIdReturnsGeneratedDatabaseId() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.nextChunkId()).thenReturn(9001L);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThat(repository.nextChunkId()).isEqualTo("9001");
    }

    @Test
    void nextChunkIdFailsClosedWhenSequenceReturnsNull() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.nextChunkId()).thenReturn(null);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(repository::nextChunkId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档切片 ID生成失败：数据库序列未返回主键");
    }

    @Test
    void resolvesActiveIndexOnlyFromTenantScopedRegistry() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        KbKnowledgeIndexActiveRow row = new KbKnowledgeIndexActiveRow();
        row.setEmbeddingModel("embedding-model");
        row.setActiveIndexVersion("kb-7001-active-v2");
        when(mapper.findActiveIndex(2001L, 7001L)).thenReturn(row);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThat(repository.findActiveIndex(TenantId.of("2001"), "7001"))
                .hasValueSatisfying(active -> {
                    assertThat(active.embeddingModel()).isEqualTo("embedding-model");
                    assertThat(active.indexVersion()).isEqualTo("kb-7001-active-v2");
                });
        verify(mapper).findActiveIndex(2001L, 7001L);
    }

    @Test
    void activatesCandidateOnlyWhenMapperConfirmsCompleteVersion() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        Instant activatedAt = Instant.parse("2026-05-25T10:00:00Z");
        when(mapper.activateIfComplete(
                2001L, 7001L, "embedding-model", "kb-7001-active-v2", activatedAt, 1001L))
                .thenReturn("kb-7001-active-v2");
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThat(repository.activateIfComplete(
                TenantId.of("2001"),
                "7001",
                "embedding-model",
                "kb-7001-active-v2",
                activatedAt,
                "1001"
        )).isTrue();
    }

    @Test
    void saveAllUpdatesOnlyTenantScopedActiveChunkRows() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbDocumentChunkPersistenceEntity.class), anyUpdate())).thenReturn(1);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        repository.saveAll(List.of(chunk()));

        verify(mapper).selectOne(anyQuery());
        verify(mapper).update(any(KbDocumentChunkPersistenceEntity.class), anyUpdate());
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbDocumentChunkPersistenceEntity.class));
    }

    @Test
    void saveAllInsertsWhenNoActiveChunkRowExists() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbDocumentChunkPersistenceEntity.class))).thenReturn(1);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        repository.saveAll(List.of(chunk()));

        verify(mapper).insert(any(KbDocumentChunkPersistenceEntity.class));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbDocumentChunkPersistenceEntity.class));
    }

    @Test
    void saveAllFailsClosedWhenActiveChunkUpdateIsLost() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbDocumentChunkPersistenceEntity.class), anyUpdate())).thenReturn(0);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.saveAll(List.of(chunk())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档切片保存失败");
    }

    @Test
    void saveAllFailsClosedWhenChunkInsertAffectsNoRows() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbDocumentChunkPersistenceEntity.class))).thenReturn(0);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.saveAll(List.of(chunk())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档切片保存失败：数据库未写入记录");
    }

    @Test
    void findByDocumentIdRejectsNonNumericDocumentIdWithChineseMessage() {
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mock(KbDocumentChunkMapper.class));

        assertThatThrownBy(() -> repository.findByDocumentId(TenantId.of("2001"), "doc-main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档 ID 必须是数字型数据库主键");
    }

    @Test
    void findByDocumentIdFailsClosedWhenMapperReturnsNullRows() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.selectList(anyQuery())).thenReturn(null);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.findByDocumentId(TenantId.of("2001"), "8001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("文档切片查询结果不能为空");
    }

    @Test
    void searchVectorCandidatesFailsClosedWhenMapperReturnsNullRows() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.searchVectorCandidates(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(null);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.searchVectorCandidates(retrievalQuery()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("向量召回查询结果不能为空");
    }

    @Test
    void searchVectorCandidatesRejectsRowsWithoutAclTokens() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.searchVectorCandidates(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(retrievalRowWithoutAclTokens()));
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.searchVectorCandidates(retrievalQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限快照不能为空");
    }

    @Test
    void searchVectorCandidatesRejectsRowsWithBlankAclTokenFragments() {
        KbDocumentChunkRetrievalRow row = retrievalRowWithoutAclTokens();
        row.setAclTokens("tenant:2001\n \nrole:admin");
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.searchVectorCandidates(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(row));
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.searchVectorCandidates(retrievalQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限快照不能为空白");
    }

    @Test
    void searchVectorCandidatesRejectsRowsWithMissingRequiredRankingFields() {
        KbDocumentChunkRetrievalRow row = retrievalRowWithoutAclTokens();
        row.setAclTokens("tenant:2001");
        row.setScore(null);
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.searchVectorCandidates(any(), any(), any(), any(), any(), any(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(row));
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.searchVectorCandidates(retrievalQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("召回分数不能为空");
    }

    @Test
    void searchKeywordCandidatesConstrainsEmbeddingModelAndIndexVersion() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.searchKeywordCandidates(any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        repository.searchKeywordCandidates(retrievalQuery());

        verify(mapper).searchKeywordCandidates(
                eq(2001L),
                eq(List.of(7001L)),
                eq("embedding-model"),
                eq("kb-7001-active"),
                eq("制度内容"),
                eq(List.of("tenant:2001", "user:1001")),
                eq(Instant.parse("2026-05-25T00:00:00Z")),
                eq(10)
        );
    }

    @Test
    void searchKeywordCandidatesFailsClosedWhenMapperReturnsNullRows() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        when(mapper.searchKeywordCandidates(any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(null);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.searchKeywordCandidates(retrievalQuery()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("关键词召回查询结果不能为空");
    }

    @Test
    void updateChunkEmbeddingRejectsBlankModelAndIndexBeforeMapperCall() {
        KbDocumentChunkMapper mapper = mock(KbDocumentChunkMapper.class);
        KbDocumentChunkRepositoryAdapter repository = new KbDocumentChunkRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.updateChunkEmbedding(
                TenantId.of("2001"),
                "9001",
                " ",
                "kb-7001-active",
                new float[1536],
                Instant.parse("2026-05-25T00:00:00Z"),
                "1001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embedding 模型不能为空白");
        assertThatThrownBy(() -> repository.updateChunkEmbedding(
                TenantId.of("2001"),
                "9001",
                "embedding-model",
                " ",
                new float[1536],
                Instant.parse("2026-05-25T00:00:00Z"),
                "1001"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("索引版本不能为空白");
        verify(mapper, never()).updateChunkEmbedding(any(), any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<KbDocumentChunkPersistenceEntity> anyQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaUpdateWrapper<KbDocumentChunkPersistenceEntity> anyUpdate() {
        return any(LambdaUpdateWrapper.class);
    }

    private static KbDocumentChunkPersistenceEntity existingEntity() {
        KbDocumentChunkPersistenceEntity entity = new KbDocumentChunkPersistenceEntity();
        entity.setId(9001L);
        entity.setTenantId(2001L);
        entity.setKnowledgeBaseId(7001L);
        entity.setDocumentId(8001L);
        entity.setChunkNo(1);
        entity.setContent("制度内容");
        entity.setContentHash("a".repeat(64));
        entity.setSourceUri("s3://aip-knowledge/documents/8001/员工手册.txt");
        entity.setDocumentVersion(1);
        entity.setTitlePath("员工手册.txt");
        entity.setClassification("internal");
        entity.setVisibility("tenant");
        entity.setAclTokens("tenant:2001");
        entity.setAuthoritative(false);
        entity.setPublishedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setEmbeddingModel("embedding-model-unassigned");
        entity.setChunkStrategyVersion("line:v1");
        entity.setIndexVersion("kb-7001-doc-v1-draft");
        entity.setTokenCount(4);
        entity.setPositionStart(0);
        entity.setPositionEnd(4);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }

    private static KnowledgeDocumentChunk chunk() {
        return new KnowledgeDocumentChunk(
                TenantId.of("2001"),
                "9001",
                "7001",
                "8001",
                1,
                "制度内容",
                "a".repeat(64),
                4,
                null,
                0,
                4,
                new KnowledgeDocumentChunkMetadata(
                        "s3://aip-knowledge/documents/8001/员工手册.txt",
                        1,
                        "员工手册.txt",
                        null,
                        null,
                        "internal",
                        "tenant",
                        List.of("tenant:2001"),
                        false,
                        Instant.parse("2026-05-22T08:00:00Z"),
                        null,
                        "embedding-model-unassigned",
                        "line:v1",
                        "kb-7001-doc-v1-draft"
                ),
                "ACTIVE",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001"
        );
    }

    private static RetrievalSearchQuery retrievalQuery() {
        return new RetrievalSearchQuery(
                TenantId.of("2001"),
                List.of("7001"),
                "制度内容",
                new float[1536],
                "embedding-model",
                "kb-7001-active",
                List.of("tenant:2001", "user:1001"),
                Instant.parse("2026-05-25T00:00:00Z"),
                10,
                0.1
        );
    }

    private static KbDocumentChunkRetrievalRow retrievalRowWithoutAclTokens() {
        KbDocumentChunkRetrievalRow row = new KbDocumentChunkRetrievalRow();
        row.setId(9001L);
        row.setTenantId(2001L);
        row.setKnowledgeBaseId(7001L);
        row.setDocumentId(8001L);
        row.setContent("制度内容");
        row.setSourceUri("s3://aip-knowledge/documents/8001/员工手册.txt");
        row.setDocumentVersion(1);
        row.setTitlePath("员工手册.txt");
        row.setClassification("internal");
        row.setVisibility("tenant");
        row.setAclTokens(null);
        row.setAuthoritative(false);
        row.setPublishedAt(Instant.parse("2026-05-22T08:00:00Z"));
        row.setEmbeddingModel("embedding-model");
        row.setChunkStrategyVersion("line:v1");
        row.setIndexVersion("kb-7001-active");
        row.setScore(0.91);
        row.setRank(1);
        return row;
    }
}
