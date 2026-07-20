package com.aiplatform.knowledge.application.indexing.service;

import com.aiplatform.knowledge.application.indexing.model.FrozenKnowledgeIndexDocument;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildRequest;
import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildResult;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildMember;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.observability.TraceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 索引 cohort 的短事务边界。调用方必须在进入此服务前完成 policy/probe 等外部工作。
 */
@Service
public class KnowledgeIndexRebuildTransactionService {
    private static final String SUPERSEDED_CODE = "AIP-KB-REBUILD-SUPERSEDED";
    private static final String SUPERSEDED_REASON = "新的 desired generation 已取代旧候选";

    private final KnowledgeIndexRebuildRepository rebuilds;
    private final KnowledgeBaseRepository knowledgeBases;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentChunkRepository chunks;
    private final DocumentIngestJobRepository jobs;
    private final Clock clock;

    public KnowledgeIndexRebuildTransactionService(
            KnowledgeIndexRebuildRepository rebuilds,
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks,
            DocumentIngestJobRepository jobs,
            Clock clock
    ) {
        this.rebuilds = Objects.requireNonNull(rebuilds, "索引重建仓储不能为空");
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.chunks = Objects.requireNonNull(chunks, "知识切片仓储不能为空");
        this.jobs = Objects.requireNonNull(jobs, "入库任务仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional
    public KnowledgeIndexRebuildResult createOrReuse(KnowledgeIndexRebuildRequest request) {
        return createOrReuseWithinTransaction(Objects.requireNonNull(request, "重建请求不能为空"));
    }

    @Transactional
    public KnowledgeIndexRebuildResult switchModelAndCreate(
            KnowledgeBase switchedKnowledgeBase,
            KnowledgeIndexRebuildRequest request
    ) {
        Objects.requireNonNull(switchedKnowledgeBase, "切换后的知识库不能为空");
        Objects.requireNonNull(request, "重建请求不能为空");
        if (!switchedKnowledgeBase.tenantId().equals(request.tenantId())
                || !switchedKnowledgeBase.knowledgeBaseId().equals(request.knowledgeBaseId())
                || !switchedKnowledgeBase.embeddingModelConfigId().equals(request.targetModelConfigId())) {
            throw new IllegalArgumentException("知识库切换事实与重建请求不一致");
        }
        rebuilds.lockKnowledgeBase(request.tenantId(), request.knowledgeBaseId());
        knowledgeBases.save(switchedKnowledgeBase);
        return scheduleAfterLock(request);
    }

    private KnowledgeIndexRebuildResult createOrReuseWithinTransaction(KnowledgeIndexRebuildRequest request) {
        rebuilds.lockKnowledgeBase(request.tenantId(), request.knowledgeBaseId());
        return scheduleAfterLock(request);
    }

    private KnowledgeIndexRebuildResult scheduleAfterLock(KnowledgeIndexRebuildRequest request) {
        Instant now = Instant.now(clock);
        var active = rebuilds.findActive(request.tenantId(), request.knowledgeBaseId());
        if (active.isPresent() && sameTarget(active.get(), request)) {
            String requestedJobId = findRequestedJob(active.get(), request);
            if (!request.hasRequestedDocument() || !requestedJobId.isEmpty()) {
                return new KnowledgeIndexRebuildResult(active.get(), requestedJobId, 0, true);
            }
            rebuilds.failActive(active.get(), SUPERSEDED_CODE, SUPERSEDED_REASON, now);
        } else {
            active.ifPresent(plan -> rebuilds.failActive(plan, SUPERSEDED_CODE, SUPERSEDED_REASON, now));
        }

        List<FrozenKnowledgeIndexDocument> members = snapshotMembers(request);
        if (members.isEmpty()) {
            if (request.hasRequestedDocument()) {
                throw new IllegalStateException("知识库没有可索引的冻结文档成员");
            }
            return KnowledgeIndexRebuildResult.empty();
        }

        KnowledgeIndexRebuildPlan plan = KnowledgeIndexRebuildPlan.active(
                request.tenantId(),
                rebuilds.nextRebuildId(),
                request.knowledgeBaseId(),
                rebuilds.nextGeneration(request.tenantId(), request.knowledgeBaseId()),
                request.targetModelConfigId(),
                request.targetEmbeddingModel(),
                now,
                request.operatorId()
        );
        rebuilds.insertPlan(plan);
        DocumentIndexJobTarget target = new DocumentIndexJobTarget(
                plan.rebuildId(), plan.targetModelConfigId(), plan.targetEmbeddingModel(), plan.indexVersion(),
                request.operatorType()
        );
        String requestedJobId = "";
        for (FrozenKnowledgeIndexDocument member : members) {
            rebuilds.insertMember(plan, member.snapshot());
            DocumentIngestJob job = DocumentIngestJob.documentIndex(
                    request.tenantId(),
                    jobs.nextJobId(),
                    member.document().documentId(),
                    request.knowledgeBaseId(),
                    TraceContext.currentTraceId(),
                    now,
                    request.operatorId()
            );
            DocumentIngestJob saved = jobs.saveIndexJob(job, target);
            if (member.document().documentId().equals(request.requestedDocumentId())) {
                requestedJobId = saved.jobId();
            }
        }
        rebuilds.pointDesired(plan, false);
        if (request.hasRequestedDocument() && requestedJobId.isEmpty()) {
            throw new IllegalStateException("请求文档不属于本次冻结索引 generation");
        }
        return new KnowledgeIndexRebuildResult(plan, requestedJobId, members.size(), false);
    }

    private List<FrozenKnowledgeIndexDocument> snapshotMembers(KnowledgeIndexRebuildRequest request) {
        ArrayList<FrozenKnowledgeIndexDocument> members = new ArrayList<>();
        for (KnowledgeDocument document : documents.findByKnowledgeBaseId(
                request.tenantId(), request.knowledgeBaseId())) {
            List<com.aiplatform.knowledge.domain.KnowledgeDocumentChunk> documentChunks =
                    chunks.findByDocumentId(request.tenantId(), document.documentId());
            if (document.indexableWith(documentChunks)) {
                members.add(new FrozenKnowledgeIndexDocument(
                        document,
                        new KnowledgeIndexRebuildMember(
                                document.documentId(), document.updatedAt(), documentChunks.size()
                        )
                ));
            }
        }
        return List.copyOf(members);
    }

    private String findRequestedJob(KnowledgeIndexRebuildPlan active, KnowledgeIndexRebuildRequest request) {
        if (!request.hasRequestedDocument()) {
            return "";
        }
        return jobs.findByRebuildDocument(
                request.tenantId(), active.rebuildId(), request.requestedDocumentId()
        ).filter(job -> job.status() != DocumentIngestStatus.FAILED)
                .map(DocumentIngestJob::jobId)
                .orElse("");
    }

    private static boolean sameTarget(KnowledgeIndexRebuildPlan active, KnowledgeIndexRebuildRequest request) {
        return active.targetModelConfigId().equals(request.targetModelConfigId())
                && active.targetEmbeddingModel().equals(request.targetEmbeddingModel());
    }

}
