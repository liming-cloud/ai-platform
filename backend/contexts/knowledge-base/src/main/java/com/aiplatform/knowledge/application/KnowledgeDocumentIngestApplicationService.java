package com.aiplatform.knowledge.application;

import com.aiplatform.knowledge.application.command.ParseDocumentIngestJobCommand;
import com.aiplatform.knowledge.application.command.ParseKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.ingest.model.PreparedKnowledgeDocumentParse;
import com.aiplatform.knowledge.application.result.ParseKnowledgeDocumentResult;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.DocumentSourceContent;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.ingest.service.KnowledgeDocumentChunkFactory;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.DocumentSourceReaderPort;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.ingest.DocumentIngestProcessorPort;
import com.aiplatform.knowledge.domain.port.ingest.model.KnowledgeDocumentChunkDraft;
import com.aiplatform.knowledge.domain.port.ingest.model.ProcessedKnowledgeDocument;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 文档入库应用服务，负责把对象存储中的文本内容解析为可向量化的文档切片。
 */
@Service
public class KnowledgeDocumentIngestApplicationService {
    private static final ErrorCode DOCUMENT_NOT_FOUND = ErrorCode.of("AIP-KB-NOTFOUND", "知识文档不存在");
    private static final ErrorCode INGEST_JOB_NOT_FOUND = ErrorCode.of("AIP-KB-JOBNOTFOUND", "文档入库任务不存在");
    private static final ErrorCode INGEST_JOB_MISMATCH = ErrorCode.of("AIP-KB-JOBMISMATCH", "文档入库任务与解析请求不一致");
    private static final ErrorCode UNSUPPORTED_INGEST_JOB = ErrorCode.of("AIP-KB-JOBUNSUPPORTED", "不支持的文档入库任务类型");
    private static final ErrorCode EMPTY_DOCUMENT = ErrorCode.of("AIP-KB-EMPTY", "文档内容为空");
    private static final ErrorCode UNSUPPORTED_CONTENT_TYPE = ErrorCode.of("AIP-KB-UNSUPPORTED", "暂不支持该文档类型解析");

    private final KnowledgeDocumentRepository documents;
    private final DocumentIngestJobRepository jobs;
    private final KnowledgeDocumentChunkRepository chunks;
    private final DocumentSourceReaderPort sourceReader;
    private final List<DocumentIngestProcessorPort> processors;
    private final KnowledgeDocumentChunkFactory chunkFactory;
    private final KnowledgeDocumentManualRetryTransactionService manualRetries;
    private final Clock clock;

    public KnowledgeDocumentIngestApplicationService(
            KnowledgeDocumentRepository documents,
            DocumentIngestJobRepository jobs,
            KnowledgeDocumentChunkRepository chunks,
            DocumentSourceReaderPort sourceReader,
            List<DocumentIngestProcessorPort> processors,
            KnowledgeDocumentChunkFactory chunkFactory,
            KnowledgeDocumentManualRetryTransactionService manualRetries,
            Clock clock
    ) {
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.jobs = Objects.requireNonNull(jobs, "文档入库任务仓储不能为空");
        this.chunks = Objects.requireNonNull(chunks, "知识文档切片仓储不能为空");
        this.sourceReader = Objects.requireNonNull(sourceReader, "文档源读取端口不能为空");
        Objects.requireNonNull(processors, "文档处理端口列表不能为空");
        this.processors = List.copyOf(processors);
        this.chunkFactory = Objects.requireNonNull(chunkFactory, "知识文档切片工厂不能为空");
        this.manualRetries = Objects.requireNonNull(manualRetries, "文档人工重排事务服务不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public ParseKnowledgeDocumentResult parseDocument(ParseKnowledgeDocumentCommand command) {
        Objects.requireNonNull(command, "文档解析命令不能为空");
        DocumentIngestJob job = jobs.findLatestByDocumentId(
                        command.tenantId(), command.knowledgeBaseId(), command.documentId())
                .orElseThrow(() -> new ApplicationException(INGEST_JOB_NOT_FOUND, "文档ID=" + command.documentId()));
        if (job.status() == DocumentIngestStatus.FAILED) {
            var retried = manualRetries.retry(
                    command.tenantId(), command.knowledgeBaseId(), command.documentId(), job,
                    command.operatorId(), Instant.now(clock)
            );
            return new ParseKnowledgeDocumentResult(
                    retried.document().documentId(), retried.job().jobId(),
                    retried.job().status().name(),
                    chunks.findByDocumentId(command.tenantId(), command.documentId()).size()
            );
        }
        return observeDocument(command.tenantId(), command.knowledgeBaseId(), command.documentId(), job);
    }

    public ParseKnowledgeDocumentResult parseDocumentIngestJob(ParseDocumentIngestJobCommand command) {
        Objects.requireNonNull(command, "文档入库事件解析命令不能为空");
        DocumentIngestJob job = jobs.findById(command.tenantId(), command.ingestJobId())
                .orElseThrow(() -> new ApplicationException(INGEST_JOB_NOT_FOUND, "任务ID=" + command.ingestJobId()));
        validateParseJob(command, job);
        return observeDocument(command.tenantId(), command.knowledgeBaseId(), command.documentId(), job);
    }

    /**
     * 在事务之外读取对象存储并解析切片。最终持久化由带 lease/fence 校验的短事务服务完成。
     */
    public PreparedKnowledgeDocumentParse prepareParse(ClaimedDocumentIngestJob claimed) {
        Objects.requireNonNull(claimed, "已认领解析任务不能为空");
        DocumentIngestJob job = claimed.job();
        if (!DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(job.jobType())) {
            throw new ApplicationException(UNSUPPORTED_INGEST_JOB, "任务ID=" + job.jobId());
        }
        KnowledgeDocument document = documents.findById(
                        job.tenantId(), job.knowledgeBaseId(), job.documentId())
                .orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + job.documentId()));
        DocumentIngestProcessorPort processor = selectProcessor(document);
        DocumentSourceContent sourceContent = sourceReader.read(document.sourceUri());
        ProcessedKnowledgeDocument processedDocument = processor.process(document, sourceContent);
        List<KnowledgeDocumentChunk> parsedChunks = toDomainChunks(
                job.operatorId(), document, processedDocument, Instant.now(clock));
        if (parsedChunks.isEmpty()) {
            throw new ApplicationException(EMPTY_DOCUMENT, "文档ID=" + document.documentId());
        }
        return new PreparedKnowledgeDocumentParse(document, parsedChunks);
    }

    private ParseKnowledgeDocumentResult observeDocument(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            DocumentIngestJob job
    ) {
        KnowledgeDocument document = documents.findById(tenantId, knowledgeBaseId, documentId)
                .orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + documentId));
        List<KnowledgeDocumentChunk> existingChunks = chunks.findByDocumentId(tenantId, documentId);
        return new ParseKnowledgeDocumentResult(
                document.documentId(), job.jobId(), document.status().name(), existingChunks.size()
        );
    }

    private void validateParseJob(ParseDocumentIngestJobCommand command, DocumentIngestJob job) {
        if (!DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(job.jobType())) {
            throw new ApplicationException(UNSUPPORTED_INGEST_JOB, "任务ID=" + job.jobId() + " 任务类型=" + job.jobType());
        }
        if (!command.knowledgeBaseId().equals(job.knowledgeBaseId()) || !command.documentId().equals(job.documentId())) {
            throw new ApplicationException(INGEST_JOB_MISMATCH, "任务ID=" + job.jobId());
        }
    }

    private DocumentIngestProcessorPort selectProcessor(KnowledgeDocument document) {
        return processors.stream()
                .filter(processor -> processor.supports(document))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(UNSUPPORTED_CONTENT_TYPE, "contentType=" + document.contentType()));
    }

    private List<KnowledgeDocumentChunk> toDomainChunks(
            String operatorId,
            KnowledgeDocument document,
            ProcessedKnowledgeDocument processedDocument,
            Instant now
    ) {
        List<KnowledgeDocumentChunkDraft> drafts = processedDocument.chunks();
        ArrayList<KnowledgeDocumentChunk> domainChunks = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            domainChunks.add(chunkFactory.createChunk(
                    document,
                    drafts.get(index),
                    chunks.nextChunkId(),
                    index + 1,
                    operatorId,
                    processedDocument.chunkStrategyVersion(),
                    now
            ));
        }
        return List.copyOf(domainChunks);
    }

}
