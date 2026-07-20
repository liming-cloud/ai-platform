package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeDocumentManualRetryTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");

    @Test
    void explicitlyRequeuesOnlyFailedParseJobAndDocumentInOneTransaction() throws Exception {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocument failedDocument = failedDocument();
        DocumentIngestJob failedJob = DocumentIngestJob.documentParse(
                failedDocument.tenantId(), "8101", failedDocument.documentId(), failedDocument.knowledgeBaseId(),
                "trace-1", NOW.minusSeconds(60), "1001"
        ).markFailed(NOW.minusSeconds(30), "provider unavailable");
        DocumentIngestJob queued = failedJob.requeueManually(NOW);
        when(documents.findById(
                failedDocument.tenantId(), failedDocument.knowledgeBaseId(), failedDocument.documentId()
        )).thenReturn(Optional.of(failedDocument));
        when(jobs.retryFailedManually(failedDocument.tenantId(), failedJob.jobId(), NOW, "1002"))
                .thenReturn(queued);
        when(documents.save(failedDocument.requeueParse(NOW))).thenReturn(failedDocument.requeueParse(NOW));
        KnowledgeDocumentManualRetryTransactionService service =
                new KnowledgeDocumentManualRetryTransactionService(documents, jobs, rebuilds);

        var result = service.retry(
                failedDocument.tenantId(), failedDocument.knowledgeBaseId(), failedDocument.documentId(),
                failedJob, "1002", NOW
        );

        assertThat(result.job().status().name()).isEqualTo("PENDING");
        assertThat(result.document().status()).isEqualTo(KnowledgeDocumentStatus.UPLOADED);
        var order = inOrder(rebuilds, documents, jobs);
        order.verify(rebuilds).lockKnowledgeBase(
                failedDocument.tenantId(), failedDocument.knowledgeBaseId()
        );
        order.verify(documents).requireForUpdate(
                failedDocument.tenantId(), failedDocument.knowledgeBaseId(), failedDocument.documentId()
        );
        order.verify(documents).findById(
                failedDocument.tenantId(), failedDocument.knowledgeBaseId(), failedDocument.documentId()
        );
        order.verify(jobs).retryFailedManually(failedDocument.tenantId(), failedJob.jobId(), NOW, "1002");
        order.verify(documents).save(failedDocument.requeueParse(NOW));
        assertThat(KnowledgeDocumentManualRetryTransactionService.class.getMethod(
                "retry", TenantId.class, String.class, String.class, DocumentIngestJob.class,
                String.class, Instant.class
        ).isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static KnowledgeDocument failedDocument() {
        return new KnowledgeDocument(
                TenantId.of("2001"), "8001", "7001", "policy.pdf", "upload",
                "s3://bucket/policy.pdf", "hash", "application/pdf", 128, 1,
                KnowledgeDocumentStatus.FAILED, NOW.minusSeconds(120), NOW.minusSeconds(30), "1001"
        );
    }
}
