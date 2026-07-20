package com.aiplatform.knowledge.application.maintenance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;
import com.aiplatform.knowledge.domain.maintenance.DocumentObjectCleanupOwnership;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectCleanupRepository;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectDeletionPort;
import com.aiplatform.knowledge.domain.port.maintenance.KnowledgeIndexMaintenancePort;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentMaintenanceWorkerTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void retainsObservedObjectWhenTheTenantDocumentNowReferencesIt() {
        DocumentObjectCleanupRepository cleanups = mock(DocumentObjectCleanupRepository.class);
        DocumentObjectDeletionPort objects = mock(DocumentObjectDeletionPort.class);
        KnowledgeIndexMaintenancePort indexes = mock(KnowledgeIndexMaintenancePort.class);
        ClaimedDocumentObjectCleanupTask claimed = task(1);
        when(cleanups.claimNext(any())).thenReturn(Optional.of(claimed), Optional.empty());
        when(cleanups.hasLiveDocumentReference(claimed, NOW)).thenReturn(true);

        worker(cleanups, objects, indexes).drainAvailable();

        verify(cleanups).retainOwned(claimed.ownership(), NOW);
        verify(objects, never()).delete(any());
        verify(cleanups, never()).completeOwned(any(), any());
    }

    @Test
    void deletesOnlyAnUnreferencedTenantObjectAndCompletesTheExactFence() {
        DocumentObjectCleanupRepository cleanups = mock(DocumentObjectCleanupRepository.class);
        DocumentObjectDeletionPort objects = mock(DocumentObjectDeletionPort.class);
        KnowledgeIndexMaintenancePort indexes = mock(KnowledgeIndexMaintenancePort.class);
        ClaimedDocumentObjectCleanupTask claimed = task(1);
        when(cleanups.claimNext(any())).thenReturn(Optional.of(claimed), Optional.empty());
        when(cleanups.hasLiveDocumentReference(claimed, NOW)).thenReturn(false);

        worker(cleanups, objects, indexes).drainAvailable();

        verify(objects).delete(claimed);
        verify(cleanups).completeOwned(claimed.ownership(), NOW);
    }

    @Test
    void retriesDeletionFailureWithBoundedBackoffAndStillRunsIndexMaintenance() {
        DocumentObjectCleanupRepository cleanups = mock(DocumentObjectCleanupRepository.class);
        DocumentObjectDeletionPort objects = mock(DocumentObjectDeletionPort.class);
        KnowledgeIndexMaintenancePort indexes = mock(KnowledgeIndexMaintenancePort.class);
        ClaimedDocumentObjectCleanupTask claimed = task(2);
        when(cleanups.claimNext(any())).thenReturn(Optional.of(claimed), Optional.empty());
        when(cleanups.hasLiveDocumentReference(claimed, NOW)).thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("MinIO token=plain-secret timeout"))
                .when(objects).delete(claimed);

        worker(cleanups, objects, indexes).drainAvailable();

        verify(cleanups).retryOwned(
                claimed.ownership(),
                "AIP-KB-OBJECT-CLEANUP-TRANSIENT",
                "MinIO token=*** timeout",
                NOW.plus(Duration.ofMinutes(2)),
                NOW
        );
        verify(indexes).deleteObsoleteEmbeddings(NOW.minus(Duration.ofHours(24)), 20);
    }

    @Test
    void finalizesWithFreshTimeAfterTheExternalDeleteInsteadOfThePreDeleteLeaseCheckTime() {
        DocumentObjectCleanupRepository cleanups = mock(DocumentObjectCleanupRepository.class);
        DocumentObjectDeletionPort objects = mock(DocumentObjectDeletionPort.class);
        KnowledgeIndexMaintenancePort indexes = mock(KnowledgeIndexMaintenancePort.class);
        Clock advancingClock = mock(Clock.class);
        ClaimedDocumentObjectCleanupTask claimed = task(1);
        when(advancingClock.instant()).thenReturn(
                NOW,
                NOW,
                NOW.plusSeconds(30),
                NOW.plusSeconds(30)
        );
        when(cleanups.claimNext(any())).thenReturn(Optional.of(claimed), Optional.empty());
        when(cleanups.hasLiveDocumentReference(claimed, NOW)).thenReturn(false);
        KnowledgeMaintenanceProperties properties = new KnowledgeMaintenanceProperties();
        properties.setBatchSize(20);
        properties.setObsoleteIndexRetention(Duration.ofHours(24));
        KnowledgeDocumentMaintenanceWorker worker = new KnowledgeDocumentMaintenanceWorker(
                cleanups, objects, indexes, properties, advancingClock, "maintenance-worker-a"
        );

        worker.drainAvailable();

        verify(cleanups).completeOwned(claimed.ownership(), NOW.plusSeconds(30));
    }

    private static KnowledgeDocumentMaintenanceWorker worker(
            DocumentObjectCleanupRepository cleanups,
            DocumentObjectDeletionPort objects,
            KnowledgeIndexMaintenancePort indexes
    ) {
        KnowledgeMaintenanceProperties properties = new KnowledgeMaintenanceProperties();
        properties.setBatchSize(20);
        properties.setMaxAttempts(5);
        properties.setLeaseDuration(Duration.ofMinutes(5));
        properties.setInitialBackoff(Duration.ofMinutes(1));
        properties.setObsoleteIndexRetention(Duration.ofHours(24));
        return new KnowledgeDocumentMaintenanceWorker(
                cleanups, objects, indexes, properties, CLOCK, "maintenance-worker-a"
        );
    }

    private static ClaimedDocumentObjectCleanupTask task(int attemptCount) {
        TenantId tenantId = TenantId.of("2001");
        return new ClaimedDocumentObjectCleanupTask(
                tenantId,
                "9101",
                "7001",
                "8001",
                "aip-knowledge",
                "tenants/2001/knowledge-bases/7001/documents/8001/hash/handbook.txt",
                "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/handbook.txt",
                "DataIntegrityViolationException",
                attemptCount,
                new DocumentObjectCleanupOwnership(
                        tenantId, "9101", "maintenance-worker-a", "lease-token", attemptCount
                )
        );
    }
}
