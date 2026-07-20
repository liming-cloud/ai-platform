package com.aiplatform.knowledge.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentJobWorkerTest {
    private static final Instant NOW = Instant.parse("2026-07-18T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void transientFailureUsesFiniteBackoffBeforeTheMaximumAttempt() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentParseJobExecutor parse = mock(KnowledgeDocumentParseJobExecutor.class);
        KnowledgeDocumentIndexJobExecutor index = mock(KnowledgeDocumentIndexJobExecutor.class);
        KnowledgeDocumentJobTransactionService transactions = mock(KnowledgeDocumentJobTransactionService.class);
        KnowledgeDocumentJobLeaseHeartbeat heartbeat = mock(KnowledgeDocumentJobLeaseHeartbeat.class);
        ClaimedDocumentIngestJob claim = parseClaim(2);
        when(jobs.claimNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(claim), Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("upstream sk-secret timeout"))
                .when(parse).execute(claim);

        KnowledgeDocumentJobWorker worker = new KnowledgeDocumentJobWorker(
                jobs, parse, index, transactions, properties(), heartbeat, CLOCK, "worker-a"
        );

        assertThat(worker.drainAvailable()).isEqualTo(1);
        verify(transactions).retry(
                claim,
                "AIP-KB-JOB-TRANSIENT",
                "upstream sk-*** timeout",
                NOW.plusSeconds(60),
                NOW
        );
        verify(transactions, never()).fail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void fifthFailedAttemptBecomesTerminalAndIsNotAutomaticallyRevived() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentParseJobExecutor parse = mock(KnowledgeDocumentParseJobExecutor.class);
        KnowledgeDocumentJobTransactionService transactions = mock(KnowledgeDocumentJobTransactionService.class);
        KnowledgeDocumentJobLeaseHeartbeat heartbeat = mock(KnowledgeDocumentJobLeaseHeartbeat.class);
        ClaimedDocumentIngestJob claim = parseClaim(5);
        when(jobs.claimNext(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(claim), Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable")).when(parse).execute(claim);

        KnowledgeDocumentJobWorker worker = new KnowledgeDocumentJobWorker(
                jobs, parse, mock(KnowledgeDocumentIndexJobExecutor.class), transactions,
                properties(), heartbeat, CLOCK, "worker-a"
        );

        worker.drainAvailable();

        verify(transactions).fail(
                claim, "AIP-KB-JOB-TRANSIENT", "provider unavailable", NOW
        );
        verify(transactions, never()).retry(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static KnowledgeDocumentJobProperties properties() {
        KnowledgeDocumentJobProperties properties = new KnowledgeDocumentJobProperties();
        properties.setBatchSize(8);
        properties.setLeaseDuration(Duration.ofMinutes(5));
        properties.setInitialBackoff(Duration.ofSeconds(30));
        properties.setMaxAttempts(5);
        return properties;
    }

    private static ClaimedDocumentIngestJob parseClaim(int attempt) {
        TenantId tenantId = TenantId.of("2001");
        DocumentIngestJob job = DocumentIngestJob.documentParse(
                tenantId, "8101", "8001", "7001", "trace-1", NOW.minusSeconds(30), "1001"
        ).markParsing(NOW);
        DocumentIngestJobOwnership ownership = new DocumentIngestJobOwnership(
                tenantId, job.jobId(), "worker-a", "lease-1", NOW.plusSeconds(300), attempt
        );
        return new ClaimedDocumentIngestJob(job, attempt, ownership, null);
    }
}
