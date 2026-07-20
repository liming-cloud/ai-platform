package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbDocumentPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbDocumentMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.Instant;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KbDocumentRepositoryAdapterTest {
    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(KbDocumentPersistenceEntity.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), "KbDocumentMapper"),
                    KbDocumentPersistenceEntity.class
            );
        }
    }

    @Test
    void nextDocumentIdReturnsGeneratedDatabaseId() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.nextDocumentId()).thenReturn(8001L);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        assertThat(repository.nextDocumentId()).isEqualTo("8001");
    }

    @Test
    void nextDocumentIdFailsClosedWhenSequenceReturnsNull() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.nextDocumentId()).thenReturn(null);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        assertThatThrownBy(repository::nextDocumentId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档 ID生成失败：数据库序列未返回主键");
    }

    @Test
    void saveUpdatesOnlyTenantScopedActiveDocumentRow() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbDocumentPersistenceEntity.class), anyUpdate())).thenReturn(1);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        repository.save(document());

        verify(mapper).selectOne(anyQuery());
        verify(mapper).update(any(KbDocumentPersistenceEntity.class), anyUpdate());
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbDocumentPersistenceEntity.class));
    }

    @Test
    void saveInsertsWhenNoActiveDocumentRowExists() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbDocumentPersistenceEntity.class))).thenReturn(1);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        repository.save(document());

        verify(mapper).insert(any(KbDocumentPersistenceEntity.class));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbDocumentPersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenActiveDocumentUpdateIsLost() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbDocumentPersistenceEntity.class), anyUpdate())).thenReturn(0);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(document()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档保存失败");
    }

    @Test
    void saveFailsClosedWhenDocumentInsertAffectsNoRows() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbDocumentPersistenceEntity.class))).thenReturn(0);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(document()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档保存失败：数据库未写入记录");
    }

    @Test
    void findByIdRejectsNonNumericDocumentIdWithChineseMessage() {
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mock(KbDocumentMapper.class));

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "7001", "doc-main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档 ID 必须是数字型数据库主键");
    }

    @Test
    void requireForUpdateLocksOnlyTheTenantScopedActiveDocument() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.lockById(2001L, 7001L, 8001L)).thenReturn(8001L);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        repository.requireForUpdate(TenantId.of("2001"), "7001", "8001");

        verify(mapper).lockById(2001L, 7001L, 8001L);
    }

    @Test
    void requireForUpdateFailsClosedWhenDocumentIsMissing() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.requireForUpdate(TenantId.of("2001"), "7001", "8001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法锁定");
    }

    @Test
    void findByKnowledgeBaseIdFailsClosedWhenMapperReturnsNullRows() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);
        when(mapper.selectList(anyQuery())).thenReturn(null);

        assertThatThrownBy(() -> repository.findByKnowledgeBaseId(TenantId.of("2001"), "7001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档查询结果不能为空");
    }

    @Test
    void removeSoftDeletesOnlyTenantScopedDocumentRow() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.update(any(), anyUpdate())).thenReturn(1);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        repository.remove(
                TenantId.of("2001"),
                "7001",
                "8001",
                "1001",
                Instant.parse("2026-05-22T08:00:00Z")
        );

        verify(mapper).update(any(), anyUpdate());
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void removeFailsClosedWhenNoDocumentRowIsUpdated() {
        KbDocumentMapper mapper = mock(KbDocumentMapper.class);
        when(mapper.update(any(), anyUpdate())).thenReturn(0);
        KbDocumentRepositoryAdapter repository = new KbDocumentRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.remove(
                        TenantId.of("2001"),
                        "7001",
                        "8001",
                        "1001",
                        Instant.parse("2026-05-22T08:00:00Z")
                ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档移除失败");
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<KbDocumentPersistenceEntity> anyQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaUpdateWrapper<KbDocumentPersistenceEntity> anyUpdate() {
        return any(LambdaUpdateWrapper.class);
    }

    private static KbDocumentPersistenceEntity existingEntity() {
        KbDocumentPersistenceEntity entity = new KbDocumentPersistenceEntity();
        entity.setId(8001L);
        entity.setTenantId(2001L);
        entity.setKnowledgeBaseId(7001L);
        entity.setDocumentName("员工手册.txt");
        entity.setSourceType("upload");
        entity.setSourceUri("s3://aip-knowledge/documents/8001/员工手册.txt");
        entity.setContentHash("d".repeat(64));
        entity.setMimeType("text/plain");
        entity.setFileSizeBytes(12L);
        entity.setParseStatus("UPLOADED");
        entity.setVersionNo(1);
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }

    private static KnowledgeDocument document() {
        return new KnowledgeDocument(
                TenantId.of("2001"),
                "8001",
                "7001",
                "员工手册.txt",
                "upload",
                "s3://aip-knowledge/documents/8001/员工手册.txt",
                "d".repeat(64),
                "text/plain",
                12L,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001"
        );
    }
}
