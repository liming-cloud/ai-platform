package com.aiplatform.knowledge.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJobOwnership;
import com.aiplatform.knowledge.domain.port.DocumentIngestJobRepository;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class KnowledgeDocumentJobLeaseHeartbeatTest {
    @Test
    void springSelectsTheProductionConstructorWhenTheSchedulerSeamAlsoExists() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    DocumentIngestJobRepository.class,
                    () -> mock(DocumentIngestJobRepository.class)
            );
            context.registerBean(KnowledgeDocumentJobProperties.class);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.register(KnowledgeDocumentJobLeaseHeartbeat.class);

            context.refresh();

            assertThat(context.getBean(KnowledgeDocumentJobLeaseHeartbeat.class)).isNotNull();
        }
    }

    @Test
    void renewsTheExactTenantScopedFenceWhileExternalWorkOwnsTheLease() {
        DocumentIngestJobRepository jobs = mock(DocumentIngestJobRepository.class);
        KnowledgeDocumentJobProperties properties = new KnowledgeDocumentJobProperties();
        properties.setLeaseDuration(Duration.ofMillis(120));
        Clock clock = Clock.systemUTC();
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        ClaimedDocumentIngestJob claimed = claim(Instant.now(clock));

        try (var heartbeat = new KnowledgeDocumentJobLeaseHeartbeat(jobs, properties, clock, scheduler);
                var ignored = heartbeat.keepAlive(claimed)) {
            ArgumentCaptor<Instant> renewedUntil = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> renewedAt = ArgumentCaptor.forClass(Instant.class);
            verify(jobs, timeout(500).atLeastOnce()).renewOwned(
                    eq(claimed.ownership()), renewedUntil.capture(), renewedAt.capture()
            );
            assertThat(renewedUntil.getValue()).isAfter(renewedAt.getValue());
            assertThat(Duration.between(renewedAt.getValue(), renewedUntil.getValue()))
                    .isEqualTo(properties.getLeaseDuration());
        }
    }

    private static ClaimedDocumentIngestJob claim(Instant now) {
        TenantId tenantId = TenantId.of("2001");
        DocumentIngestJob job = DocumentIngestJob.documentParse(
                tenantId, "8101", "8001", "7001", "trace-1", now.minusSeconds(30), "1001"
        ).markParsing(now);
        DocumentIngestJobOwnership ownership = new DocumentIngestJobOwnership(
                tenantId, job.jobId(), "worker-a", "lease-1", now.plusMillis(120), 1
        );
        return new ClaimedDocumentIngestJob(job, 1, ownership, null);
    }
}
