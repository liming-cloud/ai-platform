package com.aiplatform.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.indexing.model.KnowledgeIndexRebuildRequest;
import com.aiplatform.knowledge.application.indexing.service.KnowledgeIndexRebuildTransactionService;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeIndexRebuildTransactionServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createsOneFrozenGenerationAndIndexJobPerSnapshotMember() {
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        when(rebuilds.findActive(TENANT_ID, "16")).thenReturn(Optional.empty());
        when(rebuilds.nextRebuildId()).thenReturn("9101");
        when(rebuilds.nextGeneration(TENANT_ID, "16")).thenReturn(7L);
        KnowledgeDocument available = document("8001", KnowledgeDocumentStatus.AVAILABLE);
        KnowledgeDocument chunked = document("8002", KnowledgeDocumentStatus.CHUNKED);
        KnowledgeDocument parsing = document("8003", KnowledgeDocumentStatus.PARSING);
        when(documents.findByKnowledgeBaseId(TENANT_ID, "16"))
                .thenReturn(List.of(available, chunked, parsing));
        when(chunks.findByDocumentId(TENANT_ID, "8001"))
                .thenReturn(Collections.nCopies(1, (KnowledgeDocumentChunk) null));
        when(chunks.findByDocumentId(TENANT_ID, "8002"))
                .thenReturn(Collections.nCopies(2, (KnowledgeDocumentChunk) null));
        when(jobs.nextJobId()).thenReturn("9201", "9202");
        when(jobs.saveIndexJob(any(DocumentIngestJob.class), any(DocumentIndexJobTarget.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeIndexRebuildTransactionService service = service(rebuilds, documents, chunks, jobs);

        var result = service.createOrReuse(new KnowledgeIndexRebuildRequest(
                TENANT_ID, "16", "1001", "user", "17", "BAAI/bge-m3", "8001"
        ));

        assertThat(result.reused()).isFalse();
        assertThat(result.plan().generation()).isEqualTo(7);
        assertThat(result.plan().indexVersion()).isEqualTo("kb-16-g-7");
        assertThat(result.requestedDocumentJobId()).isEqualTo("9201");
        assertThat(result.scheduledJobCount()).isEqualTo(2);
        verify(documents, never()).save(any(KnowledgeDocument.class));
        ArgumentCaptor<DocumentIndexJobTarget> targets = ArgumentCaptor.forClass(DocumentIndexJobTarget.class);
        verify(jobs, org.mockito.Mockito.times(2)).saveIndexJob(any(DocumentIngestJob.class), targets.capture());
        assertThat(targets.getAllValues()).allSatisfy(target -> {
            assertThat(target.rebuildId()).isEqualTo("9101");
            assertThat(target.indexVersion()).isEqualTo("kb-16-g-7");
            assertThat(target.embeddingModel()).isEqualTo("BAAI/bge-m3");
        });
        verify(rebuilds).pointDesired(result.plan(), false);
    }

    @Test
    void repeatedRequestReusesMatchingActiveGenerationWithoutCreatingJobs() {
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeIndexRebuildPlan active = KnowledgeIndexRebuildPlan.active(
                TENANT_ID, "9101", "16", 7, "17", "BAAI/bge-m3", NOW, "1001"
        );
        when(rebuilds.findActive(TENANT_ID, "16")).thenReturn(Optional.of(active));
        when(jobs.findByRebuildDocument(TENANT_ID, "9101", "8001"))
                .thenReturn(Optional.of(DocumentIngestJob.documentIndex(
                        TENANT_ID, "9201", "8001", "16", "trace", NOW, "1001"
                )));
        KnowledgeIndexRebuildTransactionService service = service(rebuilds, documents, chunks, jobs);

        var result = service.createOrReuse(new KnowledgeIndexRebuildRequest(
                TENANT_ID, "16", "1001", "user", "17", "BAAI/bge-m3", "8001"
        ));

        assertThat(result.reused()).isTrue();
        assertThat(result.plan()).isEqualTo(active);
        assertThat(result.requestedDocumentJobId()).isEqualTo("9201");
        verify(rebuilds, never()).nextRebuildId();
        verify(jobs, never()).saveIndexJob(any(), any());
    }

    @Test
    void documentOutsideFrozenMembershipSupersedesOldPlanAndCreatesNextGeneration() {
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeIndexRebuildPlan active = KnowledgeIndexRebuildPlan.active(
                TENANT_ID, "9101", "16", 7, "17", "BAAI/bge-m3", NOW.minusSeconds(30), "1001"
        );
        KnowledgeDocument newDocument = document("8002", KnowledgeDocumentStatus.CHUNKED);
        when(rebuilds.findActive(TENANT_ID, "16")).thenReturn(Optional.of(active));
        when(jobs.findByRebuildDocument(TENANT_ID, active.rebuildId(), newDocument.documentId()))
                .thenReturn(Optional.empty());
        when(documents.findByKnowledgeBaseId(TENANT_ID, "16")).thenReturn(List.of(newDocument));
        when(chunks.findByDocumentId(TENANT_ID, newDocument.documentId()))
                .thenReturn(Collections.nCopies(1, (KnowledgeDocumentChunk) null));
        when(rebuilds.nextRebuildId()).thenReturn("9102");
        when(rebuilds.nextGeneration(TENANT_ID, "16")).thenReturn(8L);
        when(jobs.nextJobId()).thenReturn("9202");
        when(jobs.saveIndexJob(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeIndexRebuildTransactionService service = service(rebuilds, documents, chunks, jobs);

        var result = service.createOrReuse(new KnowledgeIndexRebuildRequest(
                TENANT_ID, "16", "1001", "user", "17", "BAAI/bge-m3", "8002"
        ));

        assertThat(result.reused()).isFalse();
        assertThat(result.plan().generation()).isEqualTo(8);
        assertThat(result.plan().indexVersion()).isEqualTo("kb-16-g-8");
        assertThat(result.requestedDocumentJobId()).isEqualTo("9202");
        verify(rebuilds).failActive(
                org.mockito.ArgumentMatchers.eq(active),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
    }

    @Test
    void failedIndexDocumentWithDurableChunksCreatesAReplacementGeneration() {
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocument failed = document("8001", KnowledgeDocumentStatus.FAILED);
        when(rebuilds.findActive(TENANT_ID, "16")).thenReturn(Optional.empty());
        when(documents.findByKnowledgeBaseId(TENANT_ID, "16")).thenReturn(List.of(failed));
        when(chunks.findByDocumentId(TENANT_ID, failed.documentId()))
                .thenReturn(Collections.nCopies(1, (KnowledgeDocumentChunk) null));
        when(rebuilds.nextRebuildId()).thenReturn("9102");
        when(rebuilds.nextGeneration(TENANT_ID, "16")).thenReturn(8L);
        when(jobs.nextJobId()).thenReturn("9202");
        when(jobs.saveIndexJob(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeIndexRebuildTransactionService service = service(rebuilds, documents, chunks, jobs);

        var result = service.createOrReuse(new KnowledgeIndexRebuildRequest(
                TENANT_ID, "16", "1001", "user", "17", "BAAI/bge-m3", failed.documentId()
        ));

        assertThat(result.requestedDocumentJobId()).isEqualTo("9202");
        assertThat(result.scheduledJobCount()).isEqualTo(1);
        verify(rebuilds).insertMember(
                org.mockito.ArgumentMatchers.eq(result.plan()),
                org.mockito.ArgumentMatchers.argThat(member -> member.documentId().equals(failed.documentId()))
        );
    }

    @Test
    void publicMutationMethodsAreShortTransactionBoundaries() throws NoSuchMethodException {
        assertThat(KnowledgeIndexRebuildTransactionService.class
                .getMethod("createOrReuse", KnowledgeIndexRebuildRequest.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static KnowledgeIndexRebuildTransactionService service(
            KnowledgeIndexRebuildRepository rebuilds,
            KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks,
            DocumentIngestJobRepository jobs
    ) {
        return new KnowledgeIndexRebuildTransactionService(
                rebuilds,
                mock(KnowledgeBaseRepository.class),
                documents,
                chunks,
                jobs,
                CLOCK
        );
    }

    private static KnowledgeDocument document(String id, KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                TENANT_ID, id, "16", id + ".md", "upload", "minio://kb/" + id,
                "hash-" + id, "text/markdown", 12, 1, status, NOW, NOW, "1001"
        );
    }
}
