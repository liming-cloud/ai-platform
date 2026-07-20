package com.aiplatform.knowledge.application;

import com.aiplatform.knowledge.application.result.KnowledgeDocumentManualRetryResult;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户显式重排失败解析作业的唯一事务边界；后台轮询永远不能调用本服务。
 */
@Service
public class KnowledgeDocumentManualRetryTransactionService {
    private final KnowledgeDocumentRepository documents;
    private final DocumentIngestJobRepository jobs;
    private final KnowledgeIndexRebuildRepository rebuilds;

    public KnowledgeDocumentManualRetryTransactionService(
            KnowledgeDocumentRepository documents,
            DocumentIngestJobRepository jobs,
            KnowledgeIndexRebuildRepository rebuilds
    ) {
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.jobs = Objects.requireNonNull(jobs, "文档作业仓储不能为空");
        this.rebuilds = Objects.requireNonNull(rebuilds, "索引重建仓储不能为空");
    }

    @Transactional
    public KnowledgeDocumentManualRetryResult retry(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            DocumentIngestJob failedJob,
            String operatorId,
            Instant now
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        Objects.requireNonNull(failedJob, "失败作业不能为空");
        Objects.requireNonNull(now, "人工重排时间不能为空");
        if (failedJob.status() != DocumentIngestStatus.FAILED
                || !failedJob.tenantId().equals(tenantId)
                || !failedJob.knowledgeBaseId().equals(knowledgeBaseId)
                || !failedJob.documentId().equals(documentId)
                || !DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(failedJob.jobType())) {
            throw new IllegalArgumentException("只有当前文档的 FAILED 解析作业才能显式重排");
        }
        rebuilds.lockKnowledgeBase(tenantId, knowledgeBaseId);
        documents.requireForUpdate(tenantId, knowledgeBaseId, documentId);
        KnowledgeDocument document = documents.findById(tenantId, knowledgeBaseId, documentId)
                .orElseThrow(() -> new IllegalStateException("人工重排的知识文档不存在"));
        DocumentIngestJob queued = jobs.retryFailedManually(
                tenantId, failedJob.jobId(), now, operatorId
        );
        KnowledgeDocument requeued = documents.save(document.requeueParse(now));
        return new KnowledgeDocumentManualRetryResult(requeued, queued);
    }
}
