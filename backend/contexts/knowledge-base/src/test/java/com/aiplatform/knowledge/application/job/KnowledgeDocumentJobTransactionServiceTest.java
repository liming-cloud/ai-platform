package com.aiplatform.knowledge.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.ingest.model.PreparedKnowledgeDocumentParse;
import com.aiplatform.knowledge.application.indexing.model.ChunkEmbedding;
import com.aiplatform.knowledge.application.indexing.model.PreparedKnowledgeDocumentIndex;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIndexJobTarget;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.DocumentIngestJobSnapshot;
import com.aiplatform.knowledge.domain.DocumentIngestStatus;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.indexing.KnowledgeIndexRebuildPlan;
import com.aiplatform.knowledge.domain.ingest.service.KnowledgeDocumentChunkFactory;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeDocumentVectorIndexRepository;
import com.aiplatform.knowledge.domain.port.indexing.KnowledgeIndexRebuildRepository;
import com.aiplatform.knowledge.domain.port.ingest.model.KnowledgeDocumentChunkDraft;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeDocumentJobTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");

    @Test
    void parseFinalizationLocksOwnershipBeforeReplacingChunksAndCompleting() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocument current = document();
        when(documents.findById(current.tenantId(), current.knowledgeBaseId(), current.documentId()))
                .thenReturn(Optional.of(current));
        ClaimedDocumentIngestJob claim = claim();
        KnowledgeDocumentChunk parsedChunk = new KnowledgeDocumentChunkFactory().createChunk(
                current,
                new KnowledgeDocumentChunkDraft("policy", 2, null, 0, 6),
                "9001",
                1,
                current.uploadedBy(),
                "token:v1",
                NOW
        );
        PreparedKnowledgeDocumentParse prepared = new PreparedKnowledgeDocumentParse(current, List.of(parsedChunk));
        KnowledgeDocumentJobTransactionService service = new KnowledgeDocumentJobTransactionService(
                jobs, documents, chunks, mock(KnowledgeDocumentVectorIndexRepository.class), rebuilds,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.completeParse(claim, prepared);

        var order = inOrder(jobs, documents, chunks, rebuilds);
        order.verify(jobs).requireOwned(claim.ownership(), NOW);
        order.verify(rebuilds).lockKnowledgeBase(current.tenantId(), current.knowledgeBaseId());
        order.verify(documents).requireForUpdate(
                current.tenantId(), current.knowledgeBaseId(), current.documentId()
        );
        order.verify(documents).findById(
                current.tenantId(), current.knowledgeBaseId(), current.documentId()
        );
        order.verify(chunks).replaceByDocumentId(
                current.tenantId(), current.knowledgeBaseId(), current.documentId(), prepared.chunks()
        );
        order.verify(documents).save(current.markChunked(NOW));
        order.verify(rebuilds).markNextGenerationRequired(
                current.tenantId(), current.knowledgeBaseId(), current.uploadedBy(), NOW
        );
        order.verify(jobs).completeOwned(claim.ownership(), NOW);
    }

    @Test
    void finalizersDeclareShortTransactionalBoundary() throws Exception {
        assertThat(KnowledgeDocumentJobTransactionService.class
                .getMethod("completeParse", ClaimedDocumentIngestJob.class, PreparedKnowledgeDocumentParse.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void indexFinalizationFencesLeaseAndLatestGenerationBeforeCandidateWrite() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        KnowledgeDocumentVectorIndexRepository vectorIndex = mock(KnowledgeDocumentVectorIndexRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        KnowledgeDocument current = document().markChunked(NOW.minusSeconds(30));
        KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunkFactory().createChunk(
                current,
                new KnowledgeDocumentChunkDraft("policy", 2, null, 0, 6),
                "9001",
                1,
                current.uploadedBy(),
                "token:v1",
                current.updatedAt()
        );
        DocumentIndexJobTarget target = new DocumentIndexJobTarget(
                "9101", "17", "BAAI/bge-m3", "kb-7001-g-2", "user"
        );
        ClaimedDocumentIngestJob claim = indexClaim(target);
        KnowledgeIndexRebuildPlan plan = KnowledgeIndexRebuildPlan.active(
                current.tenantId(), "9101", current.knowledgeBaseId(), 2,
                "17", "BAAI/bge-m3", current.updatedAt(), current.uploadedBy()
        );
        when(documents.findById(current.tenantId(), current.knowledgeBaseId(), current.documentId()))
                .thenReturn(Optional.of(current));
        when(chunks.findByDocumentId(current.tenantId(), current.documentId())).thenReturn(List.of(chunk));
        when(rebuilds.findById(current.tenantId(), plan.rebuildId())).thenReturn(Optional.of(plan));
        when(rebuilds.activateLatestIfComplete(plan, NOW, claim.job().operatorId())).thenReturn(true);
        KnowledgeDocumentJobTransactionService service = new KnowledgeDocumentJobTransactionService(
                jobs, documents, chunks, vectorIndex, rebuilds, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        PreparedKnowledgeDocumentIndex prepared = new PreparedKnowledgeDocumentIndex(
                current, target, List.of(new ChunkEmbedding(chunk.chunkId(), new float[]{0.1F, 0.2F}))
        );

        assertThat(service.completeIndex(claim, prepared)).isTrue();

        var order = inOrder(jobs, documents, chunks, rebuilds, vectorIndex);
        order.verify(jobs).requireOwned(claim.ownership(), NOW);
        order.verify(rebuilds).lockKnowledgeBase(current.tenantId(), current.knowledgeBaseId());
        order.verify(rebuilds).findById(current.tenantId(), plan.rebuildId());
        order.verify(rebuilds).lockFrozenMembers(plan);
        order.verify(documents).requireForUpdate(
                current.tenantId(), current.knowledgeBaseId(), current.documentId()
        );
        order.verify(documents).findById(
                current.tenantId(), current.knowledgeBaseId(), current.documentId()
        );
        order.verify(chunks).findByDocumentId(current.tenantId(), current.documentId());
        order.verify(rebuilds).requireLatestActive(plan);
        order.verify(rebuilds).requireCurrentMember(plan, current);
        order.verify(vectorIndex).updateChunkEmbedding(
                current.tenantId(), chunk.chunkId(), target.embeddingModel(), target.indexVersion(),
                prepared.embeddings().get(0).embedding(), NOW, claim.job().operatorId()
        );
        order.verify(rebuilds).activateLatestIfComplete(plan, NOW, claim.job().operatorId());
        order.verify(jobs).completeOwned(claim.ownership(), NOW);
    }

    @Test
    void terminalParseFailureCompletesTheJobWhenTheDocumentWasAlreadyRemoved() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        ClaimedDocumentIngestJob claim = claim();
        when(documents.findById(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), claim.job().documentId()
        )).thenReturn(Optional.empty());
        when(jobs.findLatestSnapshots(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), List.of(claim.job().documentId())
        )).thenReturn(List.of(snapshot(claim)));
        KnowledgeDocumentJobTransactionService service = new KnowledgeDocumentJobTransactionService(
                jobs,
                documents,
                mock(KnowledgeDocumentChunkRepository.class),
                mock(KnowledgeDocumentVectorIndexRepository.class),
                rebuilds,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.fail(claim, "AIP-KB-DOCUMENT-REMOVED", "文档已删除", NOW);

        var order = inOrder(jobs, rebuilds, documents);
        order.verify(jobs).requireOwned(claim.ownership(), NOW);
        order.verify(jobs).findLatestSnapshots(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), List.of(claim.job().documentId())
        );
        order.verify(rebuilds).lockKnowledgeBase(claim.job().tenantId(), claim.job().knowledgeBaseId());
        order.verify(documents).findById(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), claim.job().documentId()
        );
        order.verify(jobs).failOwned(
                claim.ownership(), "AIP-KB-DOCUMENT-REMOVED", "文档已删除", NOW
        );
        verify(documents, never()).requireForUpdate(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), claim.job().documentId()
        );
    }

    @Test
    void terminalIndexFailureMovesVectorizingDocumentToExplainableFailedState() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        DocumentIndexJobTarget target = new DocumentIndexJobTarget(
                "9101", "17", "BAAI/bge-m3", "kb-7001-g-2", "user"
        );
        ClaimedDocumentIngestJob claim = indexClaim(target);
        KnowledgeDocument vectorizing = document().markVectorizing(NOW.minusSeconds(30));
        when(documents.findById(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), claim.job().documentId()
        )).thenReturn(Optional.of(vectorizing));
        when(jobs.findLatestSnapshots(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), List.of(claim.job().documentId())
        )).thenReturn(List.of(snapshot(claim)));
        when(documents.save(vectorizing.markFailed(NOW))).thenReturn(vectorizing.markFailed(NOW));
        KnowledgeDocumentJobTransactionService service = new KnowledgeDocumentJobTransactionService(
                jobs, documents, mock(KnowledgeDocumentChunkRepository.class),
                mock(KnowledgeDocumentVectorIndexRepository.class), rebuilds,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.fail(claim, "AIP-KB-INDEX-FAILED", "embedding provider unavailable", NOW);

        var order = inOrder(jobs, rebuilds, documents);
        order.verify(jobs).requireOwned(claim.ownership(), NOW);
        order.verify(jobs).findLatestSnapshots(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), List.of(claim.job().documentId())
        );
        order.verify(rebuilds).lockKnowledgeBase(claim.job().tenantId(), claim.job().knowledgeBaseId());
        order.verify(documents).findById(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), claim.job().documentId()
        );
        order.verify(documents).requireForUpdate(
                claim.job().tenantId(), claim.job().knowledgeBaseId(), claim.job().documentId()
        );
        order.verify(documents).save(vectorizing.markFailed(NOW));
        order.verify(jobs).failOwned(
                claim.ownership(), "AIP-KB-INDEX-FAILED", "embedding provider unavailable", NOW
        );
    }

    @Test
    void staleIndexFailureCannotDowngradeDocumentOwnedByANewerRecoveryJob() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeIndexRebuildRepository rebuilds = mock(KnowledgeIndexRebuildRepository.class);
        DocumentIndexJobTarget target = new DocumentIndexJobTarget(
                "9101", "17", "BAAI/bge-m3", "kb-7001-g-2", "user"
        );
        ClaimedDocumentIngestJob staleClaim = indexClaim(target);
        when(jobs.findLatestSnapshots(
                staleClaim.job().tenantId(), staleClaim.job().knowledgeBaseId(),
                List.of(staleClaim.job().documentId())
        )).thenReturn(List.of(new DocumentIngestJobSnapshot(
                staleClaim.job().tenantId(), "8202", staleClaim.job().documentId(),
                staleClaim.job().knowledgeBaseId(), DocumentIngestJob.JOB_TYPE_DOCUMENT_INDEX,
                DocumentIngestStatus.PENDING, null, null, "trace-newer",
                NOW.plusSeconds(1), NOW.plusSeconds(1)
        )));
        KnowledgeDocumentJobTransactionService service = new KnowledgeDocumentJobTransactionService(
                jobs, documents, mock(KnowledgeDocumentChunkRepository.class),
                mock(KnowledgeDocumentVectorIndexRepository.class), rebuilds,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.fail(staleClaim, "AIP-KB-INDEX-STALE", "旧 generation 已失效", NOW);

        verify(jobs).failOwned(
                staleClaim.ownership(), "AIP-KB-INDEX-STALE", "旧 generation 已失效", NOW
        );
        verify(rebuilds, never()).lockKnowledgeBase(
                staleClaim.job().tenantId(), staleClaim.job().knowledgeBaseId()
        );
        verify(documents, never()).findById(
                staleClaim.job().tenantId(), staleClaim.job().knowledgeBaseId(),
                staleClaim.job().documentId()
        );
        verify(documents, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static ClaimedDocumentIngestJob claim() {
        DocumentIngestJob job = DocumentIngestJob.documentParse(
                TenantId.of("2001"), "8101", "8001", "7001", "trace-1", NOW.minusSeconds(30), "1001"
        ).markParsing(NOW);
        return new ClaimedDocumentIngestJob(
                job,
                1,
                new DocumentIngestJobOwnership(
                        job.tenantId(), job.jobId(), "worker-a", "lease-1", NOW.plusSeconds(300), 1
                ),
                null
        );
    }

    private static ClaimedDocumentIngestJob indexClaim(DocumentIndexJobTarget target) {
        DocumentIngestJob job = DocumentIngestJob.documentIndex(
                TenantId.of("2001"), "8201", "8001", "7001", "trace-index", NOW.minusSeconds(30), "1001"
        ).markIndexing(NOW);
        return new ClaimedDocumentIngestJob(
                job,
                1,
                new DocumentIngestJobOwnership(
                        job.tenantId(), job.jobId(), "worker-a", "lease-index", NOW.plusSeconds(300), 1
                ),
                target
        );
    }

    private static KnowledgeDocument document() {
        return new KnowledgeDocument(
                TenantId.of("2001"), "8001", "7001", "policy.pdf", "upload", "s3://bucket/policy.pdf",
                "hash", "application/pdf", 128, 1, KnowledgeDocumentStatus.UPLOADED,
                NOW.minusSeconds(60), NOW.minusSeconds(60), "1001"
        );
    }

    private static DocumentIngestJobSnapshot snapshot(ClaimedDocumentIngestJob claimed) {
        return new DocumentIngestJobSnapshot(
                claimed.job().tenantId(), claimed.job().jobId(), claimed.job().documentId(),
                claimed.job().knowledgeBaseId(), claimed.job().jobType(), claimed.job().status(),
                null, null, claimed.job().traceId(), claimed.job().submittedAt(), claimed.job().updatedAt()
        );
    }
}
