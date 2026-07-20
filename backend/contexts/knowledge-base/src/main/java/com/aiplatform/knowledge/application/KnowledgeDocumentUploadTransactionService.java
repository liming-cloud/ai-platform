package com.aiplatform.knowledge.application;

import com.aiplatform.knowledge.application.result.PersistedKnowledgeDocumentUpload;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * object-first 上传后的数据库短事务：文档事实与 PENDING 解析作业必须原子提交。
 */
@Service
public class KnowledgeDocumentUploadTransactionService {
    private final KnowledgeBaseRepository knowledgeBases;
    private final KnowledgeDocumentRepository documents;
    private final DocumentIngestJobRepository jobs;
    private final KnowledgeIndexRebuildRepository rebuilds;

    public KnowledgeDocumentUploadTransactionService(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            DocumentIngestJobRepository jobs,
            KnowledgeIndexRebuildRepository rebuilds
    ) {
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.jobs = Objects.requireNonNull(jobs, "文档作业仓储不能为空");
        this.rebuilds = Objects.requireNonNull(rebuilds, "索引重建仓储不能为空");
    }

    @Transactional
    public PersistedKnowledgeDocumentUpload persist(KnowledgeDocument document, DocumentIngestJob job) {
        Objects.requireNonNull(document, "知识文档不能为空");
        Objects.requireNonNull(job, "解析作业不能为空");
        if (!document.tenantId().equals(job.tenantId())
                || !document.knowledgeBaseId().equals(job.knowledgeBaseId())
                || !document.documentId().equals(job.documentId())
                || !DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(job.jobType())) {
            throw new IllegalArgumentException("知识文档与解析作业事实不一致");
        }
        rebuilds.lockKnowledgeBase(document.tenantId(), document.knowledgeBaseId());
        if (!knowledgeBases.existsById(document.tenantId(), document.knowledgeBaseId())) {
            throw new IllegalStateException("知识库在上传提交前已不存在");
        }
        KnowledgeDocument savedDocument = documents.save(document);
        DocumentIngestJob savedJob = jobs.save(job);
        return new PersistedKnowledgeDocumentUpload(savedDocument, savedJob);
    }
}
