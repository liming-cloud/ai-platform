package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.UploadKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.result.PersistedKnowledgeDocumentUpload;
import com.aiplatform.knowledge.domain.DocumentObject;
import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.DocumentIngestPublisher;
import com.aiplatform.knowledge.domain.port.DocumentObjectStoragePort;
import com.aiplatform.knowledge.domain.port.DocumentObjectOrphanObservationPort;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeDocumentApplicationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-22T08:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void uploadDocumentPersistsDocumentAndPendingIngestJob() {
        TraceContext.ensureTraceId("TRACE-KB-DOC-001");
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentObjectStoragePort documentObjects = mock(DocumentObjectStoragePort.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        DocumentIngestPublisher publisher = mock(DocumentIngestPublisher.class);
        KnowledgeDocumentUploadTransactionService uploadTransactions = mock(KnowledgeDocumentUploadTransactionService.class);
        stubSuccessfulUploadDependencies(knowledgeBases, documents, documentObjects, jobs);
        stubPersistedUpload(uploadTransactions);
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                knowledgeBases,
                documents,
                documentObjects,
                jobs,
                publisher,
                uploadTransactions,
                mock(DocumentObjectOrphanObservationPort.class),
                mock(KnowledgeIndexRebuildRepository.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        var result = service.uploadDocument(new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                "员工手册.pdf",
                "application/pdf",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(result.documentId()).isEqualTo("8001");
        assertThat(result.ingestJobId()).isEqualTo("8101");
        assertThat(result.status()).isEqualTo("UPLOADED");
        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        ArgumentCaptor<DocumentIngestJob> jobCaptor = ArgumentCaptor.forClass(DocumentIngestJob.class);
        verify(uploadTransactions).persist(documentCaptor.capture(), jobCaptor.capture());
        KnowledgeDocument savedDocument = documentCaptor.getValue();
        assertThat(savedDocument.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(savedDocument.knowledgeBaseId()).isEqualTo("7001");
        assertThat(savedDocument.fileName()).isEqualTo("员工手册.pdf");
        assertThat(savedDocument.contentType()).isEqualTo("application/pdf");
        assertThat(savedDocument.status()).isEqualTo(KnowledgeDocumentStatus.UPLOADED);
        assertThat(savedDocument.contentHash()).hasSize(64);
        assertThat(savedDocument.sourceUri()).startsWith("s3://aip-knowledge/");
        assertThat(savedDocument.sourceUri()).endsWith("/员工手册.pdf");
        assertThat(savedDocument.fileSizeBytes()).isEqualTo(12L);
        assertThat(savedDocument.uploadedBy()).isEqualTo("1001");
        ArgumentCaptor<DocumentObject> storedObjectCaptor = ArgumentCaptor.forClass(DocumentObject.class);
        verify(documentObjects).store(storedObjectCaptor.capture());
        DocumentObject storedObject = storedObjectCaptor.getValue();
        assertThat(storedObject.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(storedObject.knowledgeBaseId()).isEqualTo("7001");
        assertThat(storedObject.documentId()).isEqualTo("8001");
        assertThat(storedObject.fileName()).isEqualTo("员工手册.pdf");
        assertThat(storedObject.contentType()).isEqualTo("application/pdf");
        assertThat(storedObject.contentHash()).isEqualTo(savedDocument.contentHash());
        assertThat(storedObject.content()).containsExactly("制度内容".getBytes(StandardCharsets.UTF_8));
        DocumentIngestJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.jobId()).isEqualTo("8101");
        assertThat(savedJob.documentId()).isEqualTo("8001");
        assertThat(savedJob.status().name()).isEqualTo("PENDING");
        assertThat(savedJob.traceId()).isEqualTo("TRACE-KB-DOC-001");
        verify(publisher).publish(savedJob);
    }

    @Test
    void uploadDocumentKeepsPendingJobWhenPublisherFails() {
        TraceContext.ensureTraceId("TRACE-KB-DOC-002");
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentObjectStoragePort documentObjects = mock(DocumentObjectStoragePort.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        DocumentIngestPublisher publisher = mock(DocumentIngestPublisher.class);
        KnowledgeDocumentUploadTransactionService uploadTransactions = mock(KnowledgeDocumentUploadTransactionService.class);
        stubSuccessfulUploadDependencies(knowledgeBases, documents, documentObjects, jobs);
        stubPersistedUpload(uploadTransactions);
        doThrow(new IllegalStateException("RocketMQ 不可用")).when(publisher).publish(any(DocumentIngestJob.class));
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                knowledgeBases,
                documents,
                documentObjects,
                jobs,
                publisher,
                uploadTransactions,
                mock(DocumentObjectOrphanObservationPort.class),
                mock(KnowledgeIndexRebuildRepository.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        var result = service.uploadDocument(new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"),
                "1001",
                "7001",
                "员工手册.pdf",
                "application/pdf",
                "制度内容".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(result.documentId()).isEqualTo("8001");
        assertThat(result.ingestJobId()).isEqualTo("8101");
        assertThat(result.status()).isEqualTo("UPLOADED");
        verify(uploadTransactions).persist(any(KnowledgeDocument.class), any(DocumentIngestJob.class));
        verify(publisher).publish(any(DocumentIngestJob.class));
    }

    @Test
    void uploadStoresObjectOutsideTransactionAndObservesOrphanWhenDatabaseCommitFails() throws Exception {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentObjectStoragePort documentObjects = mock(DocumentObjectStoragePort.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentUploadTransactionService transactions = mock(KnowledgeDocumentUploadTransactionService.class);
        DocumentObjectOrphanObservationPort orphanObservation = mock(DocumentObjectOrphanObservationPort.class);
        DocumentIngestPublisher publisher = mock(DocumentIngestPublisher.class);
        stubSuccessfulUploadDependencies(knowledgeBases, documents, documentObjects, jobs);
        when(transactions.persist(any(KnowledgeDocument.class), any(DocumentIngestJob.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                knowledgeBases,
                documents,
                documentObjects,
                jobs,
                publisher,
                transactions,
                orphanObservation,
                mock(KnowledgeIndexRebuildRepository.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.uploadDocument(new UploadKnowledgeDocumentCommand(
                TenantId.of("2001"), "1001", "7001", "policy.pdf", "application/pdf",
                "policy".getBytes(StandardCharsets.UTF_8)
        ))).isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");

        verify(orphanObservation).observePotentialOrphan(
                org.mockito.ArgumentMatchers.eq(TenantId.of("2001")),
                org.mockito.ArgumentMatchers.eq("7001"),
                org.mockito.ArgumentMatchers.eq("8001"),
                any(StoredDocumentObject.class),
                org.mockito.ArgumentMatchers.eq("IllegalStateException")
        );
        verify(publisher, never()).publish(any());
        assertThat(KnowledgeDocumentApplicationService.class
                .getMethod("uploadDocument", UploadKnowledgeDocumentCommand.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void listDocumentsReturnsKnowledgeBaseScopedRows() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        when(knowledgeBases.existsById(TenantId.of("2001"), "7001")).thenReturn(true);
        when(documents.findByKnowledgeBaseId(TenantId.of("2001"), "7001"))
                .thenReturn(java.util.List.of(document().markFailed(FIXED_CLOCK.instant())));
        when(jobs.findLatestSnapshots(
                TenantId.of("2001"), "7001", java.util.List.of("8001")
        )).thenReturn(java.util.List.of(new DocumentIngestJobSnapshot(
                TenantId.of("2001"), "8101", "8001", "7001",
                DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE, DocumentIngestStatus.FAILED,
                "AIP-KB-PARSE-FAILED", "provider token=top-secret", "TRACE-KB-JOB-001",
                Instant.parse("2026-05-22T07:59:00Z"), Instant.parse("2026-05-22T08:00:00Z")
        )));
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                knowledgeBases,
                documents,
                mock(DocumentObjectStoragePort.class),
                jobs,
                mock(DocumentIngestPublisher.class),
                mock(KnowledgeDocumentUploadTransactionService.class),
                mock(DocumentObjectOrphanObservationPort.class),
                mock(KnowledgeIndexRebuildRepository.class),
                mock(AuditEventPublisher.class),
                FIXED_CLOCK
        );

        var results = service.listDocuments(TenantId.of("2001"), "7001");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo("8001");
        assertThat(results.get(0).fileName()).isEqualTo("员工手册.pdf");
        assertThat(results.get(0).status()).isEqualTo("FAILED");
        assertThat(results.get(0).jobId()).isEqualTo("8101");
        assertThat(results.get(0).jobType()).isEqualTo("DOCUMENT_PARSE");
        assertThat(results.get(0).jobStatus()).isEqualTo("FAILED");
        assertThat(results.get(0).failureCode()).isEqualTo("AIP-KB-PARSE-FAILED");
        assertThat(results.get(0).failureSummary()).isEqualTo("任务失败，详细信息仅在受控日志中可见");
        assertThat(results.get(0).traceId()).isEqualTo("TRACE-KB-JOB-001");
        assertThat(results.get(0).manualRetryEligible()).isTrue();
        verify(knowledgeBases).existsById(TenantId.of("2001"), "7001");
        verify(documents).findByKnowledgeBaseId(TenantId.of("2001"), "7001");
        verify(jobs).findLatestSnapshots(TenantId.of("2001"), "7001", java.util.List.of("8001"));
    }

    @Test
    void listDocumentsFailsClosedWhenJobSnapshotCrossesTenantBoundary() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        when(knowledgeBases.existsById(TenantId.of("2001"), "7001")).thenReturn(true);
        when(documents.findByKnowledgeBaseId(TenantId.of("2001"), "7001"))
                .thenReturn(java.util.List.of(document()));
        when(jobs.findLatestSnapshots(TenantId.of("2001"), "7001", java.util.List.of("8001")))
                .thenReturn(java.util.List.of(new DocumentIngestJobSnapshot(
                        TenantId.of("2002"), "8101", "8001", "7001",
                        DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE, DocumentIngestStatus.FAILED,
                        "AIP-KB-PARSE-FAILED", "解析失败", "TRACE-KB-JOB-001",
                        Instant.parse("2026-05-22T07:59:00Z"), Instant.parse("2026-05-22T08:00:00Z")
                )));
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                knowledgeBases, documents, mock(DocumentObjectStoragePort.class), jobs,
                mock(DocumentIngestPublisher.class), mock(KnowledgeDocumentUploadTransactionService.class),
                mock(DocumentObjectOrphanObservationPort.class), mock(KnowledgeIndexRebuildRepository.class),
                mock(AuditEventPublisher.class), FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.listDocuments(TenantId.of("2001"), "7001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文档作业快照越过租户或知识库边界");
    }

    @Test
    void removeDocumentSoftDeletesAndPublishesHighRiskAudit() {
        TraceContext.ensureTraceId("TRACE-KB-DOC-REMOVE-001");
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        when(documents.findById(TenantId.of("2001"), "7001", "8001")).thenReturn(Optional.of(document()));
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                mock(KnowledgeBaseRepository.class),
                documents,
                mock(DocumentObjectStoragePort.class),
                mock(DocumentIngestJobRepository.class),
                mock(DocumentIngestPublisher.class),
                mock(KnowledgeDocumentUploadTransactionService.class),
                mock(DocumentObjectOrphanObservationPort.class),
                rebuilds,
                auditPublisher,
                FIXED_CLOCK
        );

        var result = service.removeDocument(TenantId.of("2001"), "7001", "8001", "1001");

        assertThat(result.documentId()).isEqualTo("8001");
        var mutationOrder = inOrder(rebuilds, documents);
        mutationOrder.verify(rebuilds).lockKnowledgeBase(TenantId.of("2001"), "7001");
        mutationOrder.verify(documents).requireForUpdate(TenantId.of("2001"), "7001", "8001");
        mutationOrder.verify(documents).remove(
                TenantId.of("2001"), "7001", "8001", "1001", Instant.parse("2026-05-22T08:00:00Z")
        );
        mutationOrder.verify(rebuilds).markNextGenerationRequired(
                TenantId.of("2001"), "7001", "1001", Instant.parse("2026-05-22T08:00:00Z")
        );
        verify(documents).remove(TenantId.of("2001"), "7001", "8001", "1001", Instant.parse("2026-05-22T08:00:00Z"));
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("KNOWLEDGE_DOCUMENT_REMOVE");
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("8001");
        assertThat(auditCaptor.getValue().riskLevel()).isEqualTo("HIGH");
    }

    private static void stubSuccessfulUploadDependencies(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            DocumentObjectStoragePort documentObjects,
            DocumentIngestJobRepository jobs
    ) {
        when(knowledgeBases.findById(TenantId.of("2001"), "7001")).thenReturn(Optional.of(knowledgeBase()));
        when(documents.nextDocumentId()).thenReturn("8001");
        when(documents.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentObjects.store(any(DocumentObject.class))).thenAnswer(invocation -> storedObject(invocation.getArgument(0)));
        when(jobs.nextJobId()).thenReturn("8101");
        when(jobs.save(any(DocumentIngestJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void stubPersistedUpload(KnowledgeDocumentUploadTransactionService transactions) {
        when(transactions.persist(any(KnowledgeDocument.class), any(DocumentIngestJob.class)))
                .thenAnswer(invocation -> new PersistedKnowledgeDocumentUpload(
                        invocation.getArgument(0), invocation.getArgument(1)
                ));
    }

    private static KnowledgeBase knowledgeBase() {
        return new KnowledgeBase(
                TenantId.of("2001"),
                "7001",
                "企业制度知识库",
                "用于制度问答",
                KnowledgeVisibility.PUBLIC,
                Set.of(),
                "1001",
                "embedding-model-1",
                true,
                true,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z")
        );
    }

    private static KnowledgeDocument document() {
        return new KnowledgeDocument(
                TenantId.of("2001"),
                "8001",
                "7001",
                "员工手册.pdf",
                "upload",
                "s3://aip-knowledge/policy.pdf",
                "hash-1",
                "application/pdf",
                128,
                1,
                KnowledgeDocumentStatus.UPLOADED,
                Instant.parse("2026-05-22T08:00:00Z"),
                Instant.parse("2026-05-22T08:00:00Z"),
                "1001"
        );
    }

    private static StoredDocumentObject storedObject(DocumentObject documentObject) {
        String objectKey = "tenants/%s/knowledge-bases/%s/documents/%s/%s/%s".formatted(
                documentObject.tenantId().value(),
                documentObject.knowledgeBaseId(),
                documentObject.documentId(),
                documentObject.contentHash(),
                documentObject.fileName()
        );
        return new StoredDocumentObject("aip-knowledge", objectKey, "s3://aip-knowledge/" + objectKey);
    }
}
