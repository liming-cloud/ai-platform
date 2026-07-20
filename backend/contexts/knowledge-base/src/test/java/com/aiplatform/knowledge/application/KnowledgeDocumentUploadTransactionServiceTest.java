package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeDocumentUploadTransactionServiceTest {
    @Test
    void persistsDocumentAndPendingJobInOneDeclaredTransaction() throws Exception {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocument document = document();
        DocumentIngestJob job = DocumentIngestJob.documentParse(
                document.tenantId(), "8101", document.documentId(), document.knowledgeBaseId(),
                "trace-1", document.uploadedAt(), document.uploadedBy()
        );
        when(knowledgeBases.existsById(document.tenantId(), document.knowledgeBaseId())).thenReturn(true);
        when(documents.save(document)).thenReturn(document);
        when(jobs.save(job)).thenReturn(job);
        KnowledgeDocumentUploadTransactionService service = new KnowledgeDocumentUploadTransactionService(
                knowledgeBases, documents, jobs, rebuilds
        );

        var persisted = service.persist(document, job);

        assertThat(persisted.document()).isEqualTo(document);
        assertThat(persisted.job()).isEqualTo(job);
        var order = inOrder(rebuilds, knowledgeBases, documents, jobs);
        order.verify(rebuilds).lockKnowledgeBase(document.tenantId(), document.knowledgeBaseId());
        order.verify(knowledgeBases).existsById(document.tenantId(), document.knowledgeBaseId());
        order.verify(documents).save(document);
        order.verify(jobs).save(job);
        assertThat(KnowledgeDocumentUploadTransactionService.class
                .getMethod("persist", KnowledgeDocument.class, DocumentIngestJob.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static KnowledgeDocument document() {
        Instant now = Instant.parse("2026-07-18T01:00:00Z");
        return new KnowledgeDocument(
                TenantId.of("2001"), "8001", "7001", "policy.pdf", "upload",
                "s3://bucket/policy.pdf", "hash", "application/pdf", 128, 1,
                KnowledgeDocumentStatus.UPLOADED, now, now, "1001"
        );
    }
}
