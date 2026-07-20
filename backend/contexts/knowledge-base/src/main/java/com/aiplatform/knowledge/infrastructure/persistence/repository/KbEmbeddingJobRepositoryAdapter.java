package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestJobCancellation;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.infrastructure.persistence.entity.KbEmbeddingJobPersistenceEntity;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbEmbeddingJobMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import org.springframework.stereotype.Repository;

/**
 * 文档入库任务仓储 MyBatis-Plus 适配器。
 */
@Repository
public class KbEmbeddingJobRepositoryAdapter implements DocumentIngestJobRepository {
    private final KbEmbeddingJobMapper mapper;

    public KbEmbeddingJobRepositoryAdapter(KbEmbeddingJobMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "文档入库任务 Mapper 不能为空");
    }

    @Override
    public String nextJobId() {
        return requiredGeneratedId(mapper.nextJobId(), "文档入库任务 ID");
    }

    @Override
    public DocumentIngestJob save(DocumentIngestJob job) {
        return saveEntity(KbEmbeddingJobPersistenceEntity.fromDomain(job));
    }

    @Override
    public DocumentIngestJob saveIndexJob(DocumentIngestJob job, DocumentIndexJobTarget target) {
        Objects.requireNonNull(target, "索引任务目标不能为空");
        return saveEntity(KbEmbeddingJobPersistenceEntity.fromDomain(job, target));
    }

    private DocumentIngestJob saveEntity(KbEmbeddingJobPersistenceEntity entity) {
        if (findActiveRow(entity).isEmpty()) {
            requiredInsertedRows(mapper.insert(entity), "文档入库任务");
        } else {
            int updated = mapper.update(entity, activeRowUpdate(entity));
            if (updated != 1) {
                throw new IllegalStateException("文档入库任务保存失败：记录不存在或已被删除");
            }
        }
        return entity.toDomain();
    }

    @Override
    public Optional<DocumentIngestJob> findById(TenantId tenantId, String jobId) {
        KbEmbeddingJobPersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<KbEmbeddingJobPersistenceEntity>()
                .eq(KbEmbeddingJobPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbEmbeddingJobPersistenceEntity::getId, parseRequiredLong(jobId, "任务 ID"))
                .isNull(KbEmbeddingJobPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(KbEmbeddingJobPersistenceEntity::toDomain);
    }

    @Override
    public Optional<DocumentIngestJob> findLatestByDocumentId(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId
    ) {
        KbEmbeddingJobPersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<KbEmbeddingJobPersistenceEntity>()
                .eq(KbEmbeddingJobPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbEmbeddingJobPersistenceEntity::getKnowledgeBaseId,
                        parseRequiredLong(knowledgeBaseId, "知识库 ID"))
                .eq(KbEmbeddingJobPersistenceEntity::getDocumentId, parseRequiredLong(documentId, "文档 ID"))
                .isNull(KbEmbeddingJobPersistenceEntity::getDeletedAt)
                .orderByDesc(KbEmbeddingJobPersistenceEntity::getCreatedAt)
                .orderByDesc(KbEmbeddingJobPersistenceEntity::getId)
                .last("limit 1"));
        return Optional.ofNullable(row).map(KbEmbeddingJobPersistenceEntity::toDomain);
    }

    @Override
    public List<DocumentIngestJobSnapshot> findLatestSnapshots(
            TenantId tenantId,
            String knowledgeBaseId,
            List<String> documentIds
    ) {
        long requiredTenantId = parseRequiredLong(
                Objects.requireNonNull(tenantId, "租户 ID 不能为空").value(), "租户 ID"
        );
        long requiredKnowledgeBaseId = parseRequiredLong(knowledgeBaseId, "知识库 ID");
        Objects.requireNonNull(documentIds, "文档 ID 列表不能为空");
        if (documentIds.isEmpty()) {
            return List.of();
        }
        List<Long> requiredDocumentIds = documentIds.stream()
                .map(documentId -> parseRequiredLong(documentId, "文档 ID"))
                .distinct()
                .toList();
        Set<Long> expectedDocumentIds = Set.copyOf(requiredDocumentIds);
        List<DocumentIngestJobSnapshot> snapshots = mapper.findLatestSnapshots(
                        requiredTenantId, requiredKnowledgeBaseId, requiredDocumentIds
                ).stream()
                .map(KbEmbeddingJobPersistenceEntity::toSnapshot)
                .toList();
        Set<String> seenDocuments = new HashSet<>();
        for (DocumentIngestJobSnapshot snapshot : snapshots) {
            long snapshotDocumentId = parseRequiredLong(snapshot.documentId(), "文档 ID");
            if (!snapshot.tenantId().equals(tenantId)
                    || !snapshot.knowledgeBaseId().equals(knowledgeBaseId)
                    || !expectedDocumentIds.contains(snapshotDocumentId)
                    || !seenDocuments.add(snapshot.documentId())) {
                throw new IllegalStateException("最新文档作业快照越过租户、知识库或文档边界");
            }
        }
        return snapshots;
    }

    @Override
    public Optional<DocumentIngestJob> findByRebuildDocument(
            TenantId tenantId,
            String rebuildId,
            String documentId
    ) {
        KbEmbeddingJobPersistenceEntity row = mapper.selectOne(new LambdaQueryWrapper<KbEmbeddingJobPersistenceEntity>()
                .eq(KbEmbeddingJobPersistenceEntity::getTenantId, parseRequiredLong(tenantId.value(), "租户 ID"))
                .eq(KbEmbeddingJobPersistenceEntity::getRebuildId, parseRequiredLong(rebuildId, "重建计划 ID"))
                .eq(KbEmbeddingJobPersistenceEntity::getDocumentId, parseRequiredLong(documentId, "文档 ID"))
                .eq(KbEmbeddingJobPersistenceEntity::getJobType, DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX)
                .isNull(KbEmbeddingJobPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(KbEmbeddingJobPersistenceEntity::toDomain);
    }

    @Override
    public Optional<ClaimedDocumentIngestJob> claimNext(ClaimRequest request) {
        Objects.requireNonNull(request, "claim 请求不能为空");
        return Optional.ofNullable(mapper.claimNext(
                request.workerId(),
                request.leaseToken(),
                request.now(),
                request.leaseUntil(),
                request.maxAttempts()
        )).map(KbEmbeddingJobPersistenceEntity::toClaimedJob);
    }

    @Override
    public Optional<DocumentIngestJobCancellation> cancelLatest(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            String reason,
            String operatorId,
            String traceId,
            Instant now
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Instant requiredNow = Objects.requireNonNull(now, "取消时间不能为空");
        long tenantKey = parseRequiredLong(tenantId.value(), "租户 ID");
        long knowledgeBaseKey = parseRequiredLong(knowledgeBaseId, "知识库 ID");
        long documentKey = parseRequiredLong(documentId, "文档 ID");
        String requiredReason = requireText(reason, "取消理由");
        String requiredTraceId = requireText(traceId, "取消链路追踪 ID");
        long operatorKey = parseRequiredLong(operatorId, "操作人 ID");
        KbEmbeddingJobPersistenceEntity changed = mapper.cancelLatest(
                tenantKey, knowledgeBaseKey, documentKey, requiredReason, operatorKey, requiredTraceId, requiredNow
        );
        if (changed != null) {
            DocumentIngestJobSnapshot snapshot = changed.toSnapshot();
            requireCancellationScope(snapshot, tenantId, knowledgeBaseId, documentId);
            return Optional.of(new DocumentIngestJobCancellation(snapshot, true));
        }
        return findLatestSnapshots(tenantId, knowledgeBaseId, List.of(documentId)).stream()
                .findFirst()
                .filter(snapshot -> snapshot.status() == com.aiplatform.knowledge.domain.DocumentIngestStatus.CANCELLED)
                .map(snapshot -> new DocumentIngestJobCancellation(snapshot, false));
    }

    private static void requireCancellationScope(
            DocumentIngestJobSnapshot snapshot,
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId
    ) {
        if (!snapshot.tenantId().equals(tenantId)
                || !snapshot.knowledgeBaseId().equals(knowledgeBaseId)
                || !snapshot.documentId().equals(documentId)) {
            throw new IllegalStateException("取消结果越过租户、知识库或文档边界");
        }
    }

    @Override
    public DocumentIngestJob retryFailedManually(
            TenantId tenantId,
            String jobId,
            Instant now,
            String operatorId
    ) {
        long requiredJobId = parseRequiredLong(jobId, "任务 ID");
        int updated = mapper.retryFailedManually(
                parseRequiredLong(tenantId.value(), "租户 ID"),
                requiredJobId,
                Objects.requireNonNull(now, "人工重试时间不能为空"),
                parseRequiredLong(operatorId, "操作人 ID")
        );
        if (updated != 1) {
            throw new IllegalStateException("只有 FAILED 文档作业才能由用户显式重排");
        }
        return findById(tenantId, String.valueOf(requiredJobId))
                .orElseThrow(() -> new IllegalStateException("人工重排后的文档作业不存在"));
    }

    @Override
    public void requireOwned(DocumentIngestJobOwnership ownership, Instant now) {
        Objects.requireNonNull(ownership, "任务 ownership 不能为空");
        Objects.requireNonNull(now, "ownership 校验时间不能为空");
        Long lockedId = mapper.lockOwned(
                parseRequiredLong(ownership.tenantId().value(), "租户 ID"),
                parseRequiredLong(ownership.jobId(), "任务 ID"),
                ownership.leaseOwner(),
                ownership.leaseToken(),
                ownership.fenceVersion(),
                now
        );
        if (lockedId == null) {
            throw new IllegalStateException("文档入库任务租约已失效或已被其他 worker 接管");
        }
    }

    @Override
    public void renewOwned(DocumentIngestJobOwnership ownership, Instant leaseUntil, Instant now) {
        Objects.requireNonNull(ownership, "任务 ownership 不能为空");
        Instant requiredNow = Objects.requireNonNull(now, "续租时间不能为空");
        Instant requiredLeaseUntil = Objects.requireNonNull(leaseUntil, "续租截止时间不能为空");
        if (!requiredLeaseUntil.isAfter(requiredNow)) {
            throw new IllegalArgumentException("续租截止时间必须晚于续租时间");
        }
        requireMutation(mapper.renewOwned(
                tenantId(ownership), jobId(ownership), ownership.leaseOwner(), ownership.leaseToken(),
                ownership.fenceVersion(), requiredLeaseUntil, requiredNow
        ), "续租");
    }

    @Override
    public void completeOwned(DocumentIngestJobOwnership ownership, Instant now) {
        requireMutation(mapper.completeOwned(
                tenantId(ownership), jobId(ownership), ownership.leaseOwner(), ownership.leaseToken(),
                ownership.fenceVersion(), Objects.requireNonNull(now, "任务完成时间不能为空")
        ), "完成");
    }

    @Override
    public void retryOwned(
            DocumentIngestJobOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant nextRetryAt,
            Instant now
    ) {
        requireMutation(mapper.retryOwned(
                tenantId(ownership), jobId(ownership), ownership.leaseOwner(), ownership.leaseToken(),
                ownership.fenceVersion(), requireText(failureCode, "失败码"),
                requireText(failureSummary, "失败摘要"), Objects.requireNonNull(nextRetryAt, "下次重试时间不能为空"),
                Objects.requireNonNull(now, "任务失败时间不能为空")
        ), "进入重试等待");
    }

    @Override
    public void failOwned(
            DocumentIngestJobOwnership ownership,
            String failureCode,
            String failureSummary,
            Instant now
    ) {
        requireMutation(mapper.failOwned(
                tenantId(ownership), jobId(ownership), ownership.leaseOwner(), ownership.leaseToken(),
                ownership.fenceVersion(), requireText(failureCode, "失败码"),
                requireText(failureSummary, "失败摘要"), Objects.requireNonNull(now, "任务失败时间不能为空")
        ), "标记终态失败");
    }

    private Optional<KbEmbeddingJobPersistenceEntity> findActiveRow(KbEmbeddingJobPersistenceEntity entity) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<KbEmbeddingJobPersistenceEntity>()
                .eq(KbEmbeddingJobPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbEmbeddingJobPersistenceEntity::getKnowledgeBaseId, entity.getKnowledgeBaseId())
                .eq(KbEmbeddingJobPersistenceEntity::getDocumentId, entity.getDocumentId())
                .eq(KbEmbeddingJobPersistenceEntity::getId, entity.getId())
                .isNull(KbEmbeddingJobPersistenceEntity::getDeletedAt)
                .last("limit 1")));
    }

    private LambdaUpdateWrapper<KbEmbeddingJobPersistenceEntity> activeRowUpdate(KbEmbeddingJobPersistenceEntity entity) {
        return new LambdaUpdateWrapper<KbEmbeddingJobPersistenceEntity>()
                .eq(KbEmbeddingJobPersistenceEntity::getTenantId, entity.getTenantId())
                .eq(KbEmbeddingJobPersistenceEntity::getKnowledgeBaseId, entity.getKnowledgeBaseId())
                .eq(KbEmbeddingJobPersistenceEntity::getDocumentId, entity.getDocumentId())
                .eq(KbEmbeddingJobPersistenceEntity::getId, entity.getId())
                .isNull(KbEmbeddingJobPersistenceEntity::getDeletedAt);
    }

    private static String requiredGeneratedId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalStateException(fieldName + "生成失败：数据库序列未返回主键");
        }
        return String.valueOf(id);
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }

    private static long tenantId(DocumentIngestJobOwnership ownership) {
        Objects.requireNonNull(ownership, "任务 ownership 不能为空");
        return parseRequiredLong(ownership.tenantId().value(), "租户 ID");
    }

    private static long jobId(DocumentIngestJobOwnership ownership) {
        Objects.requireNonNull(ownership, "任务 ownership 不能为空");
        return parseRequiredLong(ownership.jobId(), "任务 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireMutation(int updated, String action) {
        if (updated != 1) {
            throw new IllegalStateException("文档入库任务" + action + "失败：租约已失效或 fence 不匹配");
        }
    }
}
