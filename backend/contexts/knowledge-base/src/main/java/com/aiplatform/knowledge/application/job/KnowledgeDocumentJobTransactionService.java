package com.aiplatform.knowledge.application.job;

import com.aiplatform.knowledge.application.indexing.model.ChunkEmbedding;
import com.aiplatform.knowledge.application.indexing.model.PreparedKnowledgeDocumentIndex;
import com.aiplatform.knowledge.application.ingest.model.PreparedKnowledgeDocumentParse;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeDocumentVectorIndexRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档作业的短事务 finalizer。任何对象存储、解析、授权或模型调用都必须在进入本服务前完成。
 */
@Service
public class KnowledgeDocumentJobTransactionService {
    private final DocumentIngestJobRepository jobs;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentChunkRepository chunks;
    private final KnowledgeDocumentVectorIndexRepository vectorIndex;
    private final KnowledgeIndexRebuildRepository rebuilds;
    private final Clock clock;

    public KnowledgeDocumentJobTransactionService(
            DocumentIngestJobRepository jobs,
            KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks,
            KnowledgeDocumentVectorIndexRepository vectorIndex,
            KnowledgeIndexRebuildRepository rebuilds,
            Clock clock
    ) {
        this.jobs = Objects.requireNonNull(jobs, "文档任务仓储不能为空");
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.chunks = Objects.requireNonNull(chunks, "知识切片仓储不能为空");
        this.vectorIndex = Objects.requireNonNull(vectorIndex, "向量候选仓储不能为空");
        this.rebuilds = Objects.requireNonNull(rebuilds, "索引重建仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional
    public void completeParse(
            ClaimedDocumentIngestJob claimed,
            PreparedKnowledgeDocumentParse prepared
    ) {
        requireJobType(claimed, DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE);
        Objects.requireNonNull(prepared, "解析结果不能为空");
        Instant now = Instant.now(clock);
        jobs.requireOwned(claimed.ownership(), now);
        rebuilds.lockKnowledgeBase(claimed.job().tenantId(), claimed.job().knowledgeBaseId());
        documents.requireForUpdate(
                claimed.job().tenantId(), claimed.job().knowledgeBaseId(), claimed.job().documentId()
        );
        KnowledgeDocument current = requireCurrentDocument(claimed);
        requireSameSourceSnapshot(current, prepared.sourceDocument());
        requireChunkOwnership(current, prepared.chunks());
        chunks.replaceByDocumentId(
                current.tenantId(), current.knowledgeBaseId(), current.documentId(), prepared.chunks()
        );
        documents.save(current.markChunked(now));
        rebuilds.markNextGenerationRequired(
                current.tenantId(), current.knowledgeBaseId(), claimed.job().operatorId(), now
        );
        jobs.completeOwned(claimed.ownership(), now);
    }

    @Transactional
    public boolean completeIndex(
            ClaimedDocumentIngestJob claimed,
            PreparedKnowledgeDocumentIndex prepared
    ) {
        requireJobType(claimed, DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX);
        Objects.requireNonNull(prepared, "索引结果不能为空");
        Instant now = Instant.now(clock);
        jobs.requireOwned(claimed.ownership(), now);
        rebuilds.lockKnowledgeBase(claimed.job().tenantId(), claimed.job().knowledgeBaseId());
        KnowledgeIndexRebuildPlan plan = rebuilds.findById(
                        claimed.job().tenantId(), prepared.target().rebuildId())
                .orElseThrow(() -> new IllegalStateException("索引重建计划不存在"));
        requireTarget(plan, prepared);
        rebuilds.lockFrozenMembers(plan);
        documents.requireForUpdate(
                claimed.job().tenantId(), claimed.job().knowledgeBaseId(), claimed.job().documentId()
        );
        KnowledgeDocument current = requireCurrentDocument(claimed);
        requireSameSourceSnapshot(current, prepared.sourceDocument());
        requireCurrentChunkSet(current, prepared.embeddings());
        rebuilds.requireLatestActive(plan);
        rebuilds.requireCurrentMember(plan, current);
        for (ChunkEmbedding embedding : prepared.embeddings()) {
            vectorIndex.updateChunkEmbedding(
                    current.tenantId(),
                    embedding.chunkId(),
                    prepared.target().embeddingModel(),
                    prepared.target().indexVersion(),
                    embedding.embedding(),
                    now,
                    claimed.job().operatorId()
            );
        }
        boolean activated = rebuilds.activateLatestIfComplete(plan, now, claimed.job().operatorId());
        jobs.completeOwned(claimed.ownership(), now);
        return activated;
    }

    @Transactional
    public void retry(
            ClaimedDocumentIngestJob claimed,
            String failureCode,
            String failureSummary,
            Instant nextRetryAt,
            Instant now
    ) {
        Objects.requireNonNull(claimed, "已认领任务不能为空");
        jobs.retryOwned(
                claimed.ownership(), failureCode, failureSummary,
                Objects.requireNonNull(nextRetryAt, "下次重试时间不能为空"),
                Objects.requireNonNull(now, "失败时间不能为空")
        );
    }

    @Transactional
    public void fail(
            ClaimedDocumentIngestJob claimed,
            String failureCode,
            String failureSummary,
            Instant now
    ) {
        Objects.requireNonNull(claimed, "已认领任务不能为空");
        Objects.requireNonNull(now, "失败时间不能为空");
        jobs.requireOwned(claimed.ownership(), now);
        if (DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(claimed.job().jobType())
                || DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX.equals(claimed.job().jobType())) {
            boolean latestJob = isLatestPersistedJob(claimed);
            if (!latestJob) {
                jobs.failOwned(claimed.ownership(), failureCode, failureSummary, now);
                return;
            }
            rebuilds.lockKnowledgeBase(claimed.job().tenantId(), claimed.job().knowledgeBaseId());
            if (documents.findById(
                            claimed.job().tenantId(), claimed.job().knowledgeBaseId(), claimed.job().documentId())
                    .isPresent()) {
                documents.requireForUpdate(
                        claimed.job().tenantId(), claimed.job().knowledgeBaseId(), claimed.job().documentId()
                );
                KnowledgeDocument document = requireCurrentDocument(claimed);
                documents.save(document.markFailed(now));
            }
        }
        jobs.failOwned(claimed.ownership(), failureCode, failureSummary, now);
    }

    private boolean isLatestPersistedJob(ClaimedDocumentIngestJob claimed) {
        List<DocumentIngestJobSnapshot> latest = jobs.findLatestSnapshots(
                claimed.job().tenantId(),
                claimed.job().knowledgeBaseId(),
                List.of(claimed.job().documentId())
        );
        if (latest.size() != 1) {
            throw new IllegalStateException("已认领文档作业缺少唯一的最新持久化快照");
        }
        DocumentIngestJobSnapshot snapshot = latest.get(0);
        if (!snapshot.tenantId().equals(claimed.job().tenantId())
                || !snapshot.knowledgeBaseId().equals(claimed.job().knowledgeBaseId())
                || !snapshot.documentId().equals(claimed.job().documentId())) {
            throw new IllegalStateException("最新文档作业快照越过认领边界");
        }
        return snapshot.jobId().equals(claimed.job().jobId());
    }

    private KnowledgeDocument requireCurrentDocument(ClaimedDocumentIngestJob claimed) {
        return documents.findById(
                        claimed.job().tenantId(), claimed.job().knowledgeBaseId(), claimed.job().documentId())
                .orElseThrow(() -> new IllegalStateException("作业关联文档不存在"));
    }

    private void requireCurrentChunkSet(KnowledgeDocument current, List<ChunkEmbedding> embeddings) {
        Set<String> expected = new HashSet<>();
        for (ChunkEmbedding embedding : embeddings) {
            if (!expected.add(embedding.chunkId())) {
                throw new IllegalStateException("索引结果包含重复切片");
            }
        }
        Set<String> actual = new HashSet<>();
        for (KnowledgeDocumentChunk chunk : chunks.findByDocumentId(current.tenantId(), current.documentId())) {
            actual.add(chunk.chunkId());
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException("索引执行期间冻结切片集合已变化");
        }
    }

    private static void requireChunkOwnership(
            KnowledgeDocument document,
            List<KnowledgeDocumentChunk> parsedChunks
    ) {
        for (KnowledgeDocumentChunk chunk : parsedChunks) {
            if (!document.tenantId().equals(chunk.tenantId())
                    || !document.knowledgeBaseId().equals(chunk.knowledgeBaseId())
                    || !document.documentId().equals(chunk.documentId())) {
                throw new IllegalStateException("解析切片越过文档或租户边界");
            }
        }
    }

    private static void requireSameSourceSnapshot(KnowledgeDocument current, KnowledgeDocument source) {
        if (!current.tenantId().equals(source.tenantId())
                || !current.knowledgeBaseId().equals(source.knowledgeBaseId())
                || !current.documentId().equals(source.documentId())
                || current.version() != source.version()
                || !current.contentHash().equals(source.contentHash())
                || !current.sourceUri().equals(source.sourceUri())
                || !current.updatedAt().equals(source.updatedAt())) {
            throw new IllegalStateException("外部处理期间文档源快照已变化");
        }
    }

    private static void requireTarget(
            KnowledgeIndexRebuildPlan plan,
            PreparedKnowledgeDocumentIndex prepared
    ) {
        if (!plan.tenantId().equals(prepared.sourceDocument().tenantId())
                || !plan.knowledgeBaseId().equals(prepared.sourceDocument().knowledgeBaseId())
                || !plan.targetModelConfigId().equals(prepared.target().modelConfigId())
                || !plan.targetEmbeddingModel().equals(prepared.target().embeddingModel())
                || !plan.indexVersion().equals(prepared.target().indexVersion())) {
            throw new IllegalStateException("索引任务冻结目标与重建计划不一致");
        }
    }

    private static void requireJobType(ClaimedDocumentIngestJob claimed, String expected) {
        Objects.requireNonNull(claimed, "已认领任务不能为空");
        if (!expected.equals(claimed.job().jobType())) {
            throw new IllegalArgumentException("文档作业类型不匹配");
        }
    }
}
