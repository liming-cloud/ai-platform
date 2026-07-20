package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseListItemProjection;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbKnowledgeBasePersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbKnowledgeBaseMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KbKnowledgeBaseRepositoryAdapterTest {
    @Test
    void nextKnowledgeBaseIdReturnsGeneratedDatabaseId() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.nextKnowledgeBaseId()).thenReturn(7001L);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        assertThat(repository.nextKnowledgeBaseId()).isEqualTo("7001");
    }

    @Test
    void nextKnowledgeBaseIdFailsClosedWhenSequenceReturnsNull() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.nextKnowledgeBaseId()).thenReturn(null);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        assertThatThrownBy(repository::nextKnowledgeBaseId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识库 ID生成失败：数据库序列未返回主键");
    }

    @Test
    void saveUpdatesOnlyTenantScopedActiveKnowledgeBaseRow() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbKnowledgeBasePersistenceEntity.class), anyUpdate())).thenReturn(1);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        repository.save(knowledgeBase());

        verify(mapper).selectOne(anyQuery());
        verify(mapper).update(any(KbKnowledgeBasePersistenceEntity.class), anyUpdate());
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbKnowledgeBasePersistenceEntity.class));
    }

    @Test
    void saveInsertsWhenNoActiveKnowledgeBaseRowExists() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbKnowledgeBasePersistenceEntity.class))).thenReturn(1);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        repository.save(knowledgeBase());

        verify(mapper).insert(any(KbKnowledgeBasePersistenceEntity.class));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbKnowledgeBasePersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenActiveKnowledgeBaseUpdateIsLost() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbKnowledgeBasePersistenceEntity.class), anyUpdate())).thenReturn(0);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(knowledgeBase()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识库保存失败");
    }

    @Test
    void saveFailsClosedWhenKnowledgeBaseInsertAffectsNoRows() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbKnowledgeBasePersistenceEntity.class))).thenReturn(0);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(knowledgeBase()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识库保存失败：数据库未写入记录");
    }

    @Test
    void findByIdRejectsNonNumericKnowledgeBaseIdWithChineseMessage() {
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mock(KbKnowledgeBaseMapper.class));

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "kb-main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库 ID 必须是数字型数据库主键");
    }

    @Test
    void existsByIdDoesNotRebuildLegacyDomainRow() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.selectCount(anyQuery())).thenReturn(1L);
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        assertThat(repository.existsById(TenantId.of("2001"), "7001")).isTrue();

        verify(mapper).selectCount(anyQuery());
        verify(mapper, never()).selectOne(anyQuery());
    }

    @Test
    void findListItemsByTenantReturnsManagementSelectorFields() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        when(mapper.selectList(anyQuery())).thenReturn(List.of(existingEntity()));
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        List<KnowledgeBaseListItemProjection> results = repository.findListItemsByTenant(TenantId.of("2001"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).knowledgeBaseId()).isEqualTo("7001");
        assertThat(results.get(0).name()).isEqualTo("企业制度知识库");
        assertThat(results.get(0).visibility()).isEqualTo("public");
        assertThat(results.get(0).status()).isEqualTo("ACTIVE");
        assertThat(results.get(0).embeddingModelConfigId()).isEqualTo("17");
        verify(mapper).selectList(anyQuery());
    }

    @Test
    void findListItemsByTenantAllowsLegacyRowWithoutEmbeddingModelConfigId() {
        KbKnowledgeBaseMapper mapper = mock(KbKnowledgeBaseMapper.class);
        KbKnowledgeBasePersistenceEntity legacyRow = existingEntity();
        legacyRow.setEmbeddingModelConfigId(null);
        when(mapper.selectList(anyQuery())).thenReturn(List.of(legacyRow));
        KbKnowledgeBaseRepositoryAdapter repository = new KbKnowledgeBaseRepositoryAdapter(mapper);

        List<KnowledgeBaseListItemProjection> results = repository.findListItemsByTenant(TenantId.of("2001"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).knowledgeBaseId()).isEqualTo("7001");
        assertThat(results.get(0).embeddingModelConfigId()).isEmpty();
        verify(mapper).selectList(anyQuery());
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<KbKnowledgeBasePersistenceEntity> anyQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaUpdateWrapper<KbKnowledgeBasePersistenceEntity> anyUpdate() {
        return any(LambdaUpdateWrapper.class);
    }

    private static KbKnowledgeBasePersistenceEntity existingEntity() {
        KbKnowledgeBasePersistenceEntity entity = new KbKnowledgeBasePersistenceEntity();
        entity.setId(7001L);
        entity.setTenantId(2001L);
        entity.setKbCode("KB-7001");
        entity.setKbName("企业制度知识库");
        entity.setDescription("用于制度问答");
        entity.setVisibility("public");
        entity.setEmbeddingModelConfigId(17L);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }

    private static KnowledgeBase knowledgeBase() {
        return new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                "17",
                true,
                true,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z")
        );
    }
}
