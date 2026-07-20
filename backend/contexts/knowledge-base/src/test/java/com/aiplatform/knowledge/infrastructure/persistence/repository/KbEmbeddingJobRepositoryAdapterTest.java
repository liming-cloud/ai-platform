package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.DocumentIngestJobCancellation;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbEmbeddingJobPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbEmbeddingJobMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KbEmbeddingJobRepositoryAdapterTest {
    @Test
    void heartbeatRenewsOnlyTheExactTenantLeaseTokenAndFence() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        Instant now = Instant.parse("2026-07-18T08:00:00Z");
        Instant leaseUntil = now.plusSeconds(300);
        DocumentIngestJobOwnership ownership = new DocumentIngestJobOwnership(
                TenantId.of("2001"), "8101", "worker-a", "token-a", leaseUntil, 7
        );
        when(mapper.renewOwned(2001L, 8101L, "worker-a", "token-a", 7, leaseUntil, now))
                .thenReturn(1);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        repository.renewOwned(ownership, leaseUntil, now);

        verify(mapper).renewOwned(2001L, 8101L, "worker-a", "token-a", 7, leaseUntil, now);
    }

    @Test
    void nextJobIdReturnsGeneratedDatabaseId() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        when(mapper.nextJobId()).thenReturn(8101L);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThat(repository.nextJobId()).isEqualTo("8101");
    }

    @Test
    void nextJobIdFailsClosedWhenSequenceReturnsNull() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        when(mapper.nextJobId()).thenReturn(null);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThatThrownBy(repository::nextJobId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文档入库任务 ID生成失败：数据库序列未返回主键");
    }

    @Test
    void saveUpdatesOnlyTenantScopedActiveEmbeddingJobRow() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbEmbeddingJobPersistenceEntity.class), anyUpdate())).thenReturn(1);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        repository.save(job());

        verify(mapper).selectOne(anyQuery());
        verify(mapper).update(any(KbEmbeddingJobPersistenceEntity.class), anyUpdate());
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbEmbeddingJobPersistenceEntity.class));
    }

    @Test
    void saveInsertsWhenNoActiveEmbeddingJobRowExists() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbEmbeddingJobPersistenceEntity.class))).thenReturn(1);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        repository.save(job());

        verify(mapper).insert(any(KbEmbeddingJobPersistenceEntity.class));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).updateById(any(KbEmbeddingJobPersistenceEntity.class));
    }

    @Test
    void saveFailsClosedWhenActiveEmbeddingJobUpdateIsLost() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(existingEntity());
        when(mapper.update(any(KbEmbeddingJobPersistenceEntity.class), anyUpdate())).thenReturn(0);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(job()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文档入库任务保存失败");
    }

    @Test
    void saveFailsClosedWhenEmbeddingJobInsertAffectsNoRows() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(null);
        when(mapper.insert(any(KbEmbeddingJobPersistenceEntity.class))).thenReturn(0);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.save(job()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文档入库任务保存失败：数据库未写入记录");
    }

    @Test
    void findLatestByDocumentIdRejectsNonNumericDocumentIdWithChineseMessage() {
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mock(KbEmbeddingJobMapper.class));

        assertThatThrownBy(() -> repository.findLatestByDocumentId(TenantId.of("2001"), "7001", "doc-main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档 ID 必须是数字型数据库主键");
    }

    @Test
    void findByIdRejectsNonNumericJobIdWithChineseMessage() {
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mock(KbEmbeddingJobMapper.class));

        assertThatThrownBy(() -> repository.findById(TenantId.of("2001"), "job-main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务 ID 必须是数字型数据库主键");
    }

    @Test
    void findsLatestSnapshotsWithExactTenantKnowledgeBaseAndDocumentScope() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        KbEmbeddingJobPersistenceEntity failed = existingEntity();
        failed.setStatus("FAILED");
        failed.setFailureCode("AIP-KB-PARSE-FAILED");
        failed.setFailureReason("解析失败");
        when(mapper.findLatestSnapshots(2001L, 7001L, List.of(8001L))).thenReturn(List.of(failed));
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        var snapshots = repository.findLatestSnapshots(
                TenantId.of("2001"), "7001", List.of("8001")
        );

        assertThat(snapshots).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.tenantId()).isEqualTo(TenantId.of("2001"));
            assertThat(snapshot.knowledgeBaseId()).isEqualTo("7001");
            assertThat(snapshot.documentId()).isEqualTo("8001");
            assertThat(snapshot.failureCode()).isEqualTo("AIP-KB-PARSE-FAILED");
            assertThat(snapshot.failureSummary()).isEqualTo("解析失败");
        });
        verify(mapper).findLatestSnapshots(2001L, 7001L, List.of(8001L));
    }

    @Test
    void latestSnapshotQueryRejectsCrossScopePersistenceRows() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        KbEmbeddingJobPersistenceEntity crossTenant = existingEntity();
        crossTenant.setTenantId(2002L);
        when(mapper.findLatestSnapshots(2001L, 7001L, List.of(8001L))).thenReturn(List.of(crossTenant));
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.findLatestSnapshots(
                TenantId.of("2001"), "7001", List.of("8001")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("越过租户、知识库或文档边界");
    }

    @Test
    void emptyLatestSnapshotScopeDoesNotIssueInvalidSql() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThat(repository.findLatestSnapshots(TenantId.of("2001"), "7001", List.of())).isEmpty();

        verify(mapper, never()).findLatestSnapshots(any(Long.class), any(Long.class), any());
    }

    @Test
    void cancelLatestUsesExactTenantScopeAndReturnsPersistedCancellationFacts() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        Instant now = Instant.parse("2026-07-19T08:00:00Z");
        KbEmbeddingJobPersistenceEntity cancelled = existingEntity();
        cancelled.setStatus("CANCELLED");
        cancelled.setCancelReason("发布前发现来源文件错误");
        cancelled.setCancelledBy(1001L);
        cancelled.setCancelTraceId("TRACE-KB-CANCEL-001");
        cancelled.setCancelledAt(now);
        cancelled.setUpdatedAt(now);
        when(mapper.cancelLatest(
                2001L, 7001L, 8001L, "发布前发现来源文件错误", 1001L, "TRACE-KB-CANCEL-001", now
        )).thenReturn(cancelled);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        DocumentIngestJobCancellation result = repository.cancelLatest(
                TenantId.of("2001"), "7001", "8001", "发布前发现来源文件错误",
                "1001", "TRACE-KB-CANCEL-001", now
        ).orElseThrow();

        assertThat(result.changed()).isTrue();
        assertThat(result.snapshot().status().name()).isEqualTo("CANCELLED");
        assertThat(result.snapshot().cancellationReason()).isEqualTo("发布前发现来源文件错误");
        assertThat(result.snapshot().cancelledBy()).isEqualTo("1001");
        assertThat(result.snapshot().cancellationTraceId()).isEqualTo("TRACE-KB-CANCEL-001");
        assertThat(result.snapshot().cancelledAt()).isEqualTo(now);
    }

    @Test
    void cancelLatestRejectsCrossTenantPersistenceResult() {
        KbEmbeddingJobMapper mapper = mock(KbEmbeddingJobMapper.class);
        Instant now = Instant.parse("2026-07-19T08:00:00Z");
        KbEmbeddingJobPersistenceEntity cancelled = existingEntity();
        cancelled.setTenantId(2002L);
        cancelled.setStatus("CANCELLED");
        cancelled.setCancelReason("来源文件错误");
        cancelled.setCancelledBy(1001L);
        cancelled.setCancelTraceId("TRACE-KB-CANCEL-001");
        cancelled.setCancelledAt(now);
        cancelled.setUpdatedAt(now);
        when(mapper.cancelLatest(2001L, 7001L, 8001L, "来源文件错误", 1001L, "TRACE-KB-CANCEL-001", now))
                .thenReturn(cancelled);
        KbEmbeddingJobRepositoryAdapter repository = new KbEmbeddingJobRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.cancelLatest(
                TenantId.of("2001"), "7001", "8001", "来源文件错误", "1001", "TRACE-KB-CANCEL-001", now
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("越过租户、知识库或文档边界");
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<KbEmbeddingJobPersistenceEntity> anyQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaUpdateWrapper<KbEmbeddingJobPersistenceEntity> anyUpdate() {
        return any(LambdaUpdateWrapper.class);
    }

    private static KbEmbeddingJobPersistenceEntity existingEntity() {
        KbEmbeddingJobPersistenceEntity entity = new KbEmbeddingJobPersistenceEntity();
        entity.setId(8101L);
        entity.setTenantId(2001L);
        entity.setKnowledgeBaseId(7001L);
        entity.setDocumentId(8001L);
        entity.setJobType("DOCUMENT_PARSE");
        entity.setStatus("PENDING");
        entity.setTraceId("TRACE-KB-DOC-001");
        entity.setCreatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setCreatedBy(1001L);
        entity.setUpdatedAt(Instant.parse("2026-05-22T08:00:00Z"));
        entity.setUpdatedBy(1001L);
        entity.setVersion(0);
        return entity;
    }

    private static DocumentIngestJob job() {
        return new DocumentIngestJob(
                TenantId.of("2001"),
                "8101",
                "8001",
                "7001",
                "DOCUMENT_PARSE",
                "PENDING",
                "TRACE-KB-DOC-001",
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001",
                ""
        );
    }
}
