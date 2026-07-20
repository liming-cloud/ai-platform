package com.aiplatform.knowledge.application;

import com.aiplatform.knowledge.application.command.UploadKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.command.CancelKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.result.KnowledgeDocumentListItemResult;
import com.aiplatform.knowledge.application.result.PersistedKnowledgeDocumentUpload;
import com.aiplatform.knowledge.application.result.UploadKnowledgeDocumentResult;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestJobCancellation;
import com.aiplatform.knowledge.domain.DocumentObject;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildStatus;
import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.DocumentIngestPublisher;
import com.aiplatform.knowledge.domain.port.DocumentObjectStoragePort;
import com.aiplatform.knowledge.domain.port.DocumentObjectOrphanObservationPort;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识文档应用服务，负责文档上传后的文档记录和异步入库任务创建。
 */
@Service
public class KnowledgeDocumentApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentApplicationService.class);
    private static final String SOURCE_TYPE_UPLOAD = "upload";
    private static final String UPLOADED_STATUS = "UPLOADED";
    private static final ErrorCode KNOWLEDGE_BASE_NOT_FOUND = ErrorCode.of("AIP-KB-NOTFOUND", "知识库不存在");
    private static final ErrorCode DOCUMENT_NOT_FOUND = ErrorCode.of("AIP-KB-DOCUMENTNOTFOUND", "知识文档不存在");
    private static final ErrorCode DOCUMENT_CANCEL_CONFLICT = ErrorCode.of(
            "AIP-KB-DOCUMENT-CANCEL-CONFLICT", "文档或最新作业已进入不可取消状态"
    );
    private static final String REBUILD_CANCELLED_CODE = "AIP-KB-REBUILD-CANCELLED";
    private static final String REBUILD_CANCELLED_REASON = "重建成员文档作业已由管理员取消";

    private final KnowledgeBaseRepository knowledgeBases;
    private final KnowledgeDocumentRepository documents;
    private final DocumentObjectStoragePort documentObjects;
    private final DocumentIngestJobRepository jobs;
    private final DocumentIngestPublisher ingestPublisher;
    private final KnowledgeDocumentUploadTransactionService uploadTransactions;
    private final DocumentObjectOrphanObservationPort orphanObservation;
    private final KnowledgeIndexRebuildRepository rebuilds;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    public KnowledgeDocumentApplicationService(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            DocumentObjectStoragePort documentObjects,
            DocumentIngestJobRepository jobs,
            DocumentIngestPublisher ingestPublisher,
            KnowledgeDocumentUploadTransactionService uploadTransactions,
            DocumentObjectOrphanObservationPort orphanObservation,
            KnowledgeIndexRebuildRepository rebuilds,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.knowledgeBases = Objects.requireNonNull(knowledgeBases, "知识库仓储不能为空");
        this.documents = Objects.requireNonNull(documents, "知识文档仓储不能为空");
        this.documentObjects = Objects.requireNonNull(documentObjects, "文档对象存储端口不能为空");
        this.jobs = Objects.requireNonNull(jobs, "文档入库任务仓储不能为空");
        this.ingestPublisher = Objects.requireNonNull(ingestPublisher, "文档入库发布器不能为空");
        this.uploadTransactions = Objects.requireNonNull(uploadTransactions, "文档上传短事务服务不能为空");
        this.orphanObservation = Objects.requireNonNull(orphanObservation, "孤儿对象观测端口不能为空");
        this.rebuilds = Objects.requireNonNull(rebuilds, "索引重建仓储不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public UploadKnowledgeDocumentResult uploadDocument(UploadKnowledgeDocumentCommand command) {
        Objects.requireNonNull(command, "文档上传命令不能为空");
        knowledgeBases.findById(command.tenantId(), command.knowledgeBaseId())
                .orElseThrow(() -> new ApplicationException(KNOWLEDGE_BASE_NOT_FOUND, "知识库ID=" + command.knowledgeBaseId()));

        Instant now = Instant.now(clock);
        String documentId = documents.nextDocumentId();
        String ingestJobId = jobs.nextJobId();
        byte[] content = command.content();
        String fileName = command.fileName().trim();
        String contentHash = sha256Hex(content);
        StoredDocumentObject storedObject = documentObjects.store(new DocumentObject(
                command.tenantId(),
                command.knowledgeBaseId(),
                documentId,
                fileName,
                command.contentType(),
                contentHash,
                content
        ));
        KnowledgeDocument document = new KnowledgeDocument(
                command.tenantId(),
                documentId,
                command.knowledgeBaseId(),
                fileName,
                SOURCE_TYPE_UPLOAD,
                storedObject.sourceUri(),
                contentHash,
                command.contentType(),
                content.length,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                now,
                now,
                command.operatorId()
        );
        DocumentIngestJob job = DocumentIngestJob.documentParse(
                command.tenantId(),
                ingestJobId,
                document.documentId(),
                command.knowledgeBaseId(),
                TraceContext.currentTraceId(),
                now,
                command.operatorId()
        );
        PersistedKnowledgeDocumentUpload persisted;
        try {
            persisted = uploadTransactions.persist(document, job);
        } catch (RuntimeException persistenceFailure) {
            observePotentialOrphan(command, documentId, storedObject, persistenceFailure);
            throw persistenceFailure;
        }
        publishIngestRequested(persisted.job());
        return new UploadKnowledgeDocumentResult(
                persisted.document().documentId(), persisted.job().jobId(), UPLOADED_STATUS
        );
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentListItemResult> listDocuments(TenantId tenantId, String knowledgeBaseId) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        if (!knowledgeBases.existsById(tenantId, knowledgeBaseId)) {
            throw new ApplicationException(KNOWLEDGE_BASE_NOT_FOUND, "知识库ID=" + knowledgeBaseId);
        }
        List<KnowledgeDocument> scopedDocuments = documents.findByKnowledgeBaseId(tenantId, knowledgeBaseId);
        if (scopedDocuments.isEmpty()) {
            return List.of();
        }
        List<String> documentIds = scopedDocuments.stream().map(KnowledgeDocument::documentId).toList();
        HashSet<String> expectedDocumentIds = new HashSet<>(documentIds);
        Map<String, DocumentIngestJobSnapshot> latestByDocument = new HashMap<>();
        for (DocumentIngestJobSnapshot snapshot : jobs.findLatestSnapshots(tenantId, knowledgeBaseId, documentIds)) {
            if (!snapshot.tenantId().equals(tenantId)
                    || !snapshot.knowledgeBaseId().equals(knowledgeBaseId)
                    || !expectedDocumentIds.contains(snapshot.documentId())) {
                throw new IllegalStateException("文档作业快照越过租户或知识库边界");
            }
            if (latestByDocument.putIfAbsent(snapshot.documentId(), snapshot) != null) {
                throw new IllegalStateException("文档作业快照包含重复的最新作业");
            }
        }
        return scopedDocuments.stream()
                .map(document -> KnowledgeDocumentListItemResult.from(
                        document, latestByDocument.get(document.documentId())
                ))
                .toList();
    }

    @Transactional
    public KnowledgeDocumentListItemResult removeDocument(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            String operatorId
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        rebuilds.lockKnowledgeBase(tenantId, knowledgeBaseId);
        documents.requireForUpdate(tenantId, knowledgeBaseId, documentId);
        KnowledgeDocument document = documents.findById(tenantId, knowledgeBaseId, documentId)
                .orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + documentId));
        Instant now = Instant.now(clock);
        documents.remove(tenantId, knowledgeBaseId, documentId, operatorId, now);
        rebuilds.markNextGenerationRequired(tenantId, knowledgeBaseId, operatorId, now);
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                tenantId.value(),
                operatorId,
                "KNOWLEDGE_DOCUMENT",
                documentId,
                "KNOWLEDGE_DOCUMENT_REMOVE",
                "HIGH",
                null,
                TraceContext.currentTraceId(),
                now,
                Map.of("knowledgeBaseId", knowledgeBaseId, "fileName", document.fileName())
        ));
        return KnowledgeDocumentListItemResult.from(document);
    }

    @Transactional
    public KnowledgeDocumentListItemResult cancelDocument(CancelKnowledgeDocumentCommand command) {
        Objects.requireNonNull(command, "文档取消命令不能为空");
        if (!knowledgeBases.existsById(command.tenantId(), command.knowledgeBaseId())) {
            throw new ApplicationException(KNOWLEDGE_BASE_NOT_FOUND, "知识库ID=" + command.knowledgeBaseId());
        }
        KnowledgeDocument preflightDocument = documents.findById(
                command.tenantId(), command.knowledgeBaseId(), command.documentId()
        ).orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + command.documentId()));
        Instant now = Instant.now(clock);
        DocumentIngestJobCancellation cancellation = jobs.cancelLatest(
                command.tenantId(), command.knowledgeBaseId(), command.documentId(), command.reason(),
                command.operatorId(), command.traceId(), now
        ).orElseThrow(() -> cancelConflict(preflightDocument, "最新作业不存在或已终结"));

        rebuilds.lockKnowledgeBase(command.tenantId(), command.knowledgeBaseId());
        try {
            documents.requireForUpdate(command.tenantId(), command.knowledgeBaseId(), command.documentId());
        } catch (IllegalStateException missingDuringCancellation) {
            throw new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + command.documentId());
        }
        KnowledgeDocument lockedDocument = documents.findById(
                command.tenantId(), command.knowledgeBaseId(), command.documentId()
        ).orElseThrow(() -> new ApplicationException(DOCUMENT_NOT_FOUND, "文档ID=" + command.documentId()));
        DocumentIngestJobSnapshot latest = jobs.findLatestSnapshots(
                command.tenantId(), command.knowledgeBaseId(), List.of(command.documentId())
        ).stream().findFirst().orElseThrow(() -> cancelConflict(lockedDocument, "取消后最新作业缺失"));
        if (!latest.jobId().equals(cancellation.snapshot().jobId())
                || latest.status() != com.aiplatform.knowledge.domain.DocumentIngestStatus.CANCELLED) {
            throw cancelConflict(lockedDocument, "取消期间出现新的作业或状态已变化");
        }

        KnowledgeDocument resultDocument = lockedDocument;
        if (cancellation.changed()) {
            if (!lockedDocument.cancellable()) {
                throw cancelConflict(lockedDocument, "文档已进入终态");
            }
            documents.cancel(
                    command.tenantId(), command.knowledgeBaseId(), command.documentId(), command.operatorId(), now
            );
            resultDocument = lockedDocument.markCancelled(now);
            cancelActiveRebuildIfRequired(command, lockedDocument, latest, now);
            auditPublisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    command.tenantId().value(),
                    command.operatorId(),
                    "KNOWLEDGE_DOCUMENT_JOB",
                    latest.jobId(),
                    "KNOWLEDGE_DOCUMENT_JOB_CANCEL",
                    "HIGH",
                    null,
                    command.traceId(),
                    now,
                    Map.of(
                            "knowledgeBaseId", command.knowledgeBaseId(),
                            "documentId", command.documentId(),
                            "fileName", lockedDocument.fileName(),
                            "reason", command.reason(),
                            "previousDocumentStatus", lockedDocument.status().name(),
                            "jobId", latest.jobId(),
                            "jobType", latest.jobType(),
                            "resultStatus", latest.status().name()
                    )
            ));
        } else if (lockedDocument.status() != KnowledgeDocumentStatus.CANCELLED) {
            throw cancelConflict(lockedDocument, "作业与文档取消状态不一致");
        }
        return KnowledgeDocumentListItemResult.from(resultDocument, latest);
    }

    private void cancelActiveRebuildIfRequired(
            CancelKnowledgeDocumentCommand command,
            KnowledgeDocument document,
            DocumentIngestJobSnapshot latest,
            Instant now
    ) {
        if (!DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX.equals(latest.jobType())) {
            return;
        }
        if (latest.rebuildId() == null) {
            throw cancelConflict(document, "索引作业缺少冻结重建计划");
        }
        var plan = rebuilds.findById(command.tenantId(), latest.rebuildId())
                .orElseThrow(() -> cancelConflict(document, "索引重建计划不存在"));
        if (plan.status() != KnowledgeIndexRebuildStatus.ACTIVE
                || !plan.knowledgeBaseId().equals(command.knowledgeBaseId())) {
            throw cancelConflict(document, "索引重建计划已终结或范围不一致");
        }
        rebuilds.failActive(
                plan, REBUILD_CANCELLED_CODE, REBUILD_CANCELLED_REASON, now, command.operatorId()
        );
        rebuilds.markNextGenerationRequired(
                command.tenantId(), command.knowledgeBaseId(), command.operatorId(), now
        );
    }

    private static ApplicationException cancelConflict(KnowledgeDocument document, String detail) {
        return new ApplicationException(
                DOCUMENT_CANCEL_CONFLICT,
                "文档ID=" + document.documentId() + " 当前状态=" + document.status().name() + "，" + detail
        );
    }

    private void publishIngestRequested(DocumentIngestJob job) {
        try {
            ingestPublisher.publish(job);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "文档入库事件发布失败，任务保持待处理状态：tenantId={}, knowledgeBaseId={}, documentId={}, jobId={}, traceId={}",
                    job.tenantId().value(),
                    job.knowledgeBaseId(),
                    job.documentId(),
                    job.jobId(),
                    job.traceId(),
                    exception
            );
        }
    }

    private void observePotentialOrphan(
            UploadKnowledgeDocumentCommand command,
            String documentId,
            StoredDocumentObject storedObject,
            RuntimeException persistenceFailure
    ) {
        try {
            orphanObservation.observePotentialOrphan(
                    command.tenantId(), command.knowledgeBaseId(), documentId, storedObject,
                    persistenceFailure.getClass().getSimpleName()
            );
        } catch (RuntimeException observationFailure) {
            LOGGER.error(
                    "孤儿对象观测失败：tenantId={}, knowledgeBaseId={}, documentId={}, bucket={}, objectKey={}",
                    command.tenantId().value(), command.knowledgeBaseId(), documentId,
                    storedObject.bucket(), storedObject.objectKey(), observationFailure
            );
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }
}
