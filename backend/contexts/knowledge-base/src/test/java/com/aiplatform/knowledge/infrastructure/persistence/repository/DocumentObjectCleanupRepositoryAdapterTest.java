package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.application.maintenance.KnowledgeMaintenanceProperties;
import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.knowledge.domain.maintenance.DocumentObjectCleanupOwnership;
import com.aiplatform.knowledge.domain.port.maintenance.DocumentObjectCleanupRepository;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.DocumentObjectCleanupMapper;
import com.aiplatform.knowledge.infrastructure.persistence.projection.DocumentObjectCleanupRow;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DocumentObjectCleanupRepositoryAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void observationPersistsAnIdempotentCleanupAfterTheSafetyGracePeriod() {
        DocumentObjectCleanupMapper mapper = mock(DocumentObjectCleanupMapper.class);
        KnowledgeMaintenanceProperties properties = properties();
        when(mapper.observe(
                2001L, 7001L, 8001L, "aip-knowledge",
                "tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                "Data_Integrity_Failure", NOW, NOW.plus(Duration.ofHours(2))
        )).thenReturn(1);
        DocumentObjectCleanupRepositoryAdapter adapter = new DocumentObjectCleanupRepositoryAdapter(
                mapper, properties, CLOCK
        );

        adapter.observePotentialOrphan(
                TenantId.of("2001"), "7001", "8001",
                new StoredDocumentObject(
                        "aip-knowledge",
                        "tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                        "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt"
                ),
                "Data Integrity Failure"
        );

        verify(mapper).observe(
                2001L, 7001L, 8001L, "aip-knowledge",
                "tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                "Data_Integrity_Failure", NOW, NOW.plus(Duration.ofHours(2))
        );
    }

    @Test
    void mapsDatabaseGeneratedCleanupAndFenceAsTheOnlyOwnershipFacts() {
        DocumentObjectCleanupMapper mapper = mock(DocumentObjectCleanupMapper.class);
        DocumentObjectCleanupRow row = row();
        when(mapper.claimNext("worker-a", "token-a", NOW, NOW.plusSeconds(300), 5)).thenReturn(row);
        DocumentObjectCleanupRepositoryAdapter adapter = new DocumentObjectCleanupRepositoryAdapter(
                mapper, properties(), CLOCK
        );

        var claimed = adapter.claimNext(new DocumentObjectCleanupRepository.ClaimRequest(
                "worker-a", "token-a", NOW, NOW.plusSeconds(300), 5
        )).orElseThrow();

        assertThat(claimed.cleanupId()).isEqualTo("9101");
        assertThat(claimed.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(claimed.attemptCount()).isEqualTo(2);
        assertThat(claimed.ownership()).isEqualTo(new DocumentObjectCleanupOwnership(
                TenantId.of("2001"), "9101", "worker-a", "token-a", 7
        ));
    }

    @Test
    void missingOwnedRowFailsClosedBeforeExternalDeletion() {
        DocumentObjectCleanupMapper mapper = mock(DocumentObjectCleanupMapper.class);
        DocumentObjectCleanupRepositoryAdapter adapter = new DocumentObjectCleanupRepositoryAdapter(
                mapper, properties(), CLOCK
        );
        var claimed = adapter.claimNext(new DocumentObjectCleanupRepository.ClaimRequest(
                "worker-a", "token-a", NOW, NOW.plusSeconds(300), 5
        ));
        assertThat(claimed).isEmpty();

        var ownership = new DocumentObjectCleanupOwnership(
                TenantId.of("2001"), "9101", "worker-a", "token-a", 7
        );
        var task = new com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask(
                TenantId.of("2001"), "9101", "7001", "8001", "aip-knowledge",
                "tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                "s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt",
                "DataIntegrityViolationException", 2, ownership
        );
        when(mapper.hasLiveDocumentReference(2001L, 9101L, "worker-a", "token-a", 7, NOW))
                .thenReturn(null);

        assertThatThrownBy(() -> adapter.hasLiveDocumentReference(task, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fence");
    }

    private static KnowledgeMaintenanceProperties properties() {
        KnowledgeMaintenanceProperties properties = new KnowledgeMaintenanceProperties();
        properties.setOrphanGracePeriod(Duration.ofHours(2));
        return properties;
    }

    private static DocumentObjectCleanupRow row() {
        DocumentObjectCleanupRow row = new DocumentObjectCleanupRow();
        row.setId(9101L);
        row.setTenantId(2001L);
        row.setKnowledgeBaseId(7001L);
        row.setDocumentId(8001L);
        row.setBucket("aip-knowledge");
        row.setObjectKey("tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt");
        row.setSourceUri("s3://aip-knowledge/tenants/2001/knowledge-bases/7001/documents/8001/hash/file.txt");
        row.setObservationFailureType("DataIntegrityViolationException");
        row.setAttemptCount(2);
        row.setLeaseOwner("worker-a");
        row.setLeaseToken("token-a");
        row.setVersion(7);
        return row;
    }
}
