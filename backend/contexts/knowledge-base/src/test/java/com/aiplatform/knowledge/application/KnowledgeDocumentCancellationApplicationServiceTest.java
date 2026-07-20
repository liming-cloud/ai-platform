package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.CancelKnowledgeDocumentCommand;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobCancellation;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.DocumentIngestPublisher;
import com.aiplatform.knowledge.domain.port.DocumentObjectOrphanObservationPort;
import com.aiplatform.knowledge.domain.port.DocumentObjectStoragePort;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeDocumentCancellationApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T08:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final TenantId TENANT = TenantId.of("2001");

    @Test
    void cancelsLatestJobThenLocksAndCancelsDocumentWithDurableAuditEvidence() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        KnowledgeDocument running = document(KnowledgeDocumentStatus.PARSING);
        DocumentIngestJobSnapshot cancelled = cancelledSnapshot();
        when(knowledgeBases.existsById(TENANT, "7001")).thenReturn(true);
        when(documents.findById(TENANT, "7001", "8001")).thenReturn(Optional.of(running));
        when(jobs.cancelLatest(
                TENANT, "7001", "8001", "来源文件错误", "1001", "TRACE-CANCEL-001", NOW
        )).thenReturn(Optional.of(new DocumentIngestJobCancellation(cancelled, true)));
        when(jobs.findLatestSnapshots(TENANT, "7001", List.of("8001"))).thenReturn(List.of(cancelled));
        KnowledgeDocumentApplicationService service = service(knowledgeBases, documents, jobs, audit);

        var result = service.cancelDocument(command("来源文件错误"));

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.jobStatus()).isEqualTo("CANCELLED");
        assertThat(result.cancellationReason()).isEqualTo("来源文件错误");
        assertThat(result.cancelEligible()).isFalse();
        var ordered = inOrder(jobs, documents);
        ordered.verify(jobs).cancelLatest(
                TENANT, "7001", "8001", "来源文件错误", "1001", "TRACE-CANCEL-001", NOW
        );
        ordered.verify(documents).requireForUpdate(TENANT, "7001", "8001");
        ordered.verify(jobs).findLatestSnapshots(TENANT, "7001", List.of("8001"));
        ordered.verify(documents).cancel(TENANT, "7001", "8001", "1001", NOW);
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().tenantId()).isEqualTo("2001");
        assertThat(auditCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(auditCaptor.getValue().traceId()).isEqualTo("TRACE-CANCEL-001");
        assertThat(auditCaptor.getValue().action()).isEqualTo("KNOWLEDGE_DOCUMENT_JOB_CANCEL");
        assertThat(auditCaptor.getValue().detailAttributes()).containsEntry("reason", "来源文件错误");
    }

    @Test
    void repeatedCancellationReturnsPersistedFactsWithoutSecondMutationOrAudit() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        KnowledgeDocument cancelledDocument = document(KnowledgeDocumentStatus.CANCELLED);
        DocumentIngestJobSnapshot cancelled = cancelledSnapshot();
        when(knowledgeBases.existsById(TENANT, "7001")).thenReturn(true);
        when(documents.findById(TENANT, "7001", "8001")).thenReturn(Optional.of(cancelledDocument));
        when(jobs.cancelLatest(
                TENANT, "7001", "8001", "另一个理由", "1001", "TRACE-CANCEL-001", NOW
        )).thenReturn(Optional.of(new DocumentIngestJobCancellation(cancelled, false)));
        when(jobs.findLatestSnapshots(TENANT, "7001", List.of("8001"))).thenReturn(List.of(cancelled));
        KnowledgeDocumentApplicationService service = service(knowledgeBases, documents, jobs, audit);

        var result = service.cancelDocument(command("另一个理由"));

        assertThat(result.cancellationReason()).isEqualTo("来源文件错误");
        verify(documents, never()).cancel(TENANT, "7001", "8001", "1001", NOW);
        verify(audit, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancellingIndexJobFailsItsExactActiveGenerationAndRequiresReplacement() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        KnowledgeDocument running = document(KnowledgeDocumentStatus.VECTORIZING);
        DocumentIngestJobSnapshot cancelled = new DocumentIngestJobSnapshot(
                TENANT, "8102", "8001", "7001", DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX,
                DocumentIngestStatus.CANCELLED, null, null, "TRACE-JOB-INDEX-001",
                NOW.minusSeconds(600), NOW, "来源文件错误", "1001", "TRACE-CANCEL-001", NOW, "9201"
        );
        KnowledgeIndexRebuildPlan plan = KnowledgeIndexRebuildPlan.active(
                TENANT, "9201", "7001", 3, "6101", "embedding-large", NOW.minusSeconds(900), "1001"
        );
        when(knowledgeBases.existsById(TENANT, "7001")).thenReturn(true);
        when(documents.findById(TENANT, "7001", "8001")).thenReturn(Optional.of(running));
        when(jobs.cancelLatest(
                TENANT, "7001", "8001", "来源文件错误", "1001", "TRACE-CANCEL-001", NOW
        )).thenReturn(Optional.of(new DocumentIngestJobCancellation(cancelled, true)));
        when(jobs.findLatestSnapshots(TENANT, "7001", List.of("8001"))).thenReturn(List.of(cancelled));
        when(rebuilds.findById(TENANT, "9201")).thenReturn(Optional.of(plan));
        KnowledgeDocumentApplicationService service = service(knowledgeBases, documents, jobs, rebuilds, audit);

        service.cancelDocument(command("来源文件错误"));

        verify(rebuilds).failActive(
                plan, "AIP-KB-REBUILD-CANCELLED", "重建成员文档作业已由管理员取消", NOW, "1001"
        );
        verify(rebuilds).markNextGenerationRequired(TENANT, "7001", "1001", NOW);
    }

    @Test
    void failsClosedWhenLatestJobIsAlreadyTerminal() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        when(knowledgeBases.existsById(TENANT, "7001")).thenReturn(true);
        when(documents.findById(TENANT, "7001", "8001"))
                .thenReturn(Optional.of(document(KnowledgeDocumentStatus.AVAILABLE)));
        when(jobs.cancelLatest(
                TENANT, "7001", "8001", "来源文件错误", "1001", "TRACE-CANCEL-001", NOW
        )).thenReturn(Optional.empty());
        KnowledgeDocumentApplicationService service = service(
                knowledgeBases, documents, jobs, mock(AuditEventPublisher.class)
        );

        assertThatThrownBy(() -> service.cancelDocument(command("来源文件错误")))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).errorCode().code())
                        .isEqualTo("AIP-KB-DOCUMENT-CANCEL-CONFLICT"));
        verify(documents, never()).requireForUpdate(TENANT, "7001", "8001");
    }

    @Test
    void rollsBackCancellationWhenAnotherJobBecomesLatestBeforeDocumentLock() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        AuditEventPublisher audit = mock(AuditEventPublisher.class);
        KnowledgeDocument running = document(KnowledgeDocumentStatus.PARSING);
        DocumentIngestJobSnapshot cancelled = cancelledSnapshot();
        DocumentIngestJobSnapshot newer = new DocumentIngestJobSnapshot(
                TENANT, "8102", "8001", "7001", DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE,
                DocumentIngestStatus.PENDING, null, null, "TRACE-JOB-NEW", NOW, NOW
        );
        when(knowledgeBases.existsById(TENANT, "7001")).thenReturn(true);
        when(documents.findById(TENANT, "7001", "8001")).thenReturn(Optional.of(running));
        when(jobs.cancelLatest(
                TENANT, "7001", "8001", "来源文件错误", "1001", "TRACE-CANCEL-001", NOW
        )).thenReturn(Optional.of(new DocumentIngestJobCancellation(cancelled, true)));
        when(jobs.findLatestSnapshots(TENANT, "7001", List.of("8001"))).thenReturn(List.of(newer));
        KnowledgeDocumentApplicationService service = service(knowledgeBases, documents, jobs, audit);

        assertThatThrownBy(() -> service.cancelDocument(command("来源文件错误")))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).errorCode().code())
                        .isEqualTo("AIP-KB-DOCUMENT-CANCEL-CONFLICT"));
        verify(documents, never()).cancel(TENANT, "7001", "8001", "1001", NOW);
        verify(audit, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    private static KnowledgeDocumentApplicationService service(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            DocumentIngestJobRepository jobs,
            AuditEventPublisher audit
    ) {
        return service(knowledgeBases, documents, jobs, mock(KnowledgeIndexRebuildRepository.class), audit);
    }

    private static KnowledgeDocumentApplicationService service(
            KnowledgeBaseRepository knowledgeBases,
            KnowledgeDocumentRepository documents,
            DocumentIngestJobRepository jobs,
            KnowledgeIndexRebuildRepository rebuilds,
            AuditEventPublisher audit
    ) {
        return new KnowledgeDocumentApplicationService(
                knowledgeBases,
                documents,
                mock(DocumentObjectStoragePort.class),
                jobs,
                mock(DocumentIngestPublisher.class),
                mock(KnowledgeDocumentUploadTransactionService.class),
                mock(DocumentObjectOrphanObservationPort.class),
                rebuilds,
                audit,
                CLOCK
        );
    }

    private static CancelKnowledgeDocumentCommand command(String reason) {
        return new CancelKnowledgeDocumentCommand(
                TENANT, "1001", "7001", "8001", reason, "TRACE-CANCEL-001"
        );
    }

    private static DocumentIngestJobSnapshot cancelledSnapshot() {
        return new DocumentIngestJobSnapshot(
                TENANT, "8101", "8001", "7001", DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE,
                DocumentIngestStatus.CANCELLED, null, null, "TRACE-JOB-001",
                NOW.minusSeconds(600), NOW, "来源文件错误", "1001", "TRACE-CANCEL-001", NOW
        );
    }

    private static KnowledgeDocument document(KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                TENANT, "8001", "7001", "policy.pdf", "upload", "s3://bucket/policy.pdf",
                "hash", "application/pdf", 128, 1, status, NOW.minusSeconds(1200), NOW.minusSeconds(60), "1001"
        );
    }
}
