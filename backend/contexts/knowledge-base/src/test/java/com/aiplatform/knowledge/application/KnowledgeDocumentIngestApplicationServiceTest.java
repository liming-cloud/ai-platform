package com.aiplatform.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.command.ParseKnowledgeDocumentCommand;
import com.aiplatform.knowledge.application.ingest.model.PreparedKnowledgeDocumentParse;
import com.aiplatform.knowledge.application.result.KnowledgeDocumentManualRetryResult;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.DocumentSourceContent;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunk;
import com.aiplatform.knowledge.domain.KnowledgeDocumentStatus;
import com.aiplatform.knowledge.domain.ingest.service.KnowledgeDocumentChunkFactory;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.knowledge.domain.port.DocumentSourceReaderPort;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentChunkRepository;
import com.aiplatform.knowledge.domain.port.KnowledgeDocumentRepository;
import com.aiplatform.knowledge.domain.port.ingest.DocumentIngestProcessorPort;
import com.aiplatform.knowledge.domain.port.ingest.model.KnowledgeDocumentChunkDraft;
import com.aiplatform.knowledge.domain.port.ingest.model.ProcessedKnowledgeDocument;
import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentIngestApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-06-06T02:20:00Z");

    @Test
    void preparesFailedDocumentOutsideTransactionWithoutMutatingDurableFacts() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        DocumentSourceReaderPort sourceReader = mock(DocumentSourceReaderPort.class);
        DocumentIngestProcessorPort processor = mock(DocumentIngestProcessorPort.class);
        KnowledgeDocumentChunkFactory chunkFactory = new KnowledgeDocumentChunkFactory();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        KnowledgeDocumentIngestApplicationService service = new KnowledgeDocumentIngestApplicationService(
                documents,
                jobs,
                chunks,
                sourceReader,
                List.of(processor),
                chunkFactory,
                mock(KnowledgeDocumentManualRetryTransactionService.class),
                clock
        );

        KnowledgeDocument document = new KnowledgeDocument(
                TENANT_ID,
                "2",
                "3",
                "wf10.txt",
                "upload",
                "s3://aip-knowledge/wf10.txt",
                "hash-1",
                "text/plain",
                13,
                1,
                KnowledgeDocumentStatus.FAILED,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60),
                "1001"
        );
        DocumentIngestJob job = DocumentIngestJob.documentParse(
                TENANT_ID,
                "3",
                "2",
                "3",
                "trace-wf10-parse",
                NOW.minusSeconds(30),
                "1001"
        );
        KnowledgeDocumentChunk rebuiltChunk = chunkFactory.createChunk(
                document,
                new KnowledgeDocumentChunkDraft("制度内容", 4, null, 0, 4),
                "4",
                1,
                "1001",
                "token:v1",
                NOW
        );

        when(documents.findById(TENANT_ID, "3", "2")).thenReturn(Optional.of(document));
        when(chunks.nextChunkId()).thenReturn("4");
        when(processor.supports(document)).thenReturn(true);
        when(sourceReader.read("s3://aip-knowledge/wf10.txt"))
                .thenReturn(new DocumentSourceContent("s3://aip-knowledge/wf10.txt", "制度内容\n".getBytes(StandardCharsets.UTF_8)));
        when(processor.process(any(KnowledgeDocument.class), any(DocumentSourceContent.class)))
                .thenReturn(new ProcessedKnowledgeDocument(
                        "plain-text",
                        "token:v1",
                        List.of(new KnowledgeDocumentChunkDraft("制度内容", 4, null, 0, 4))
                ));
        DocumentIngestJob running = job.markParsing(NOW);
        ClaimedDocumentIngestJob claimed = new ClaimedDocumentIngestJob(
                running,
                1,
                new DocumentIngestJobOwnership(
                        TENANT_ID, running.jobId(), "worker-a", "lease-1", NOW.plusSeconds(300), 1
                ),
                null
        );

        PreparedKnowledgeDocumentParse result = service.prepareParse(claimed);

        assertThat(result.sourceDocument()).isEqualTo(document);
        assertThat(result.chunks()).containsExactly(rebuiltChunk);
        verify(chunks, never()).replaceByDocumentId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
        verify(documents, never()).save(any());
        verify(jobs, never()).save(any());
    }

    @Test
    void explicitParseRequestRequeuesFailedJobButBackgroundObservationDoesNot() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentChunkRepository chunks = mock(KnowledgeDocumentChunkRepository.class);
        KnowledgeDocumentManualRetryTransactionService manualRetries =
                mock(KnowledgeDocumentManualRetryTransactionService.class);
        KnowledgeDocument document = new KnowledgeDocument(
                TENANT_ID, "2", "3", "wf10.txt", "upload", "s3://aip-knowledge/wf10.txt",
                "hash-1", "text/plain", 13, 1, KnowledgeDocumentStatus.FAILED,
                NOW.minusSeconds(60), NOW.minusSeconds(30), "1001"
        );
        DocumentIngestJob failed = DocumentIngestJob.documentParse(
                TENANT_ID, "3", "2", "3", "trace", NOW.minusSeconds(60), "1001"
        ).markFailed(NOW.minusSeconds(30), "parse failed");
        DocumentIngestJob queued = failed.requeueManually(NOW);
        when(jobs.findLatestByDocumentId(TENANT_ID, "3", "2")).thenReturn(Optional.of(failed));
        when(manualRetries.retry(TENANT_ID, "3", "2", failed, "1002", NOW))
                .thenReturn(new KnowledgeDocumentManualRetryResult(
                        document.requeueParse(NOW), queued
                ));
        when(chunks.findByDocumentId(TENANT_ID, "2")).thenReturn(List.of());
        KnowledgeDocumentIngestApplicationService service = new KnowledgeDocumentIngestApplicationService(
                documents,
                jobs,
                chunks,
                mock(DocumentSourceReaderPort.class),
                List.of(),
                new KnowledgeDocumentChunkFactory(),
                manualRetries,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.parseDocument(new ParseKnowledgeDocumentCommand(
                TENANT_ID, "1002", "3", "2"
        ));

        assertThat(result.status()).isEqualTo("PENDING");
        verify(manualRetries).retry(TENANT_ID, "3", "2", failed, "1002", NOW);
    }
}
