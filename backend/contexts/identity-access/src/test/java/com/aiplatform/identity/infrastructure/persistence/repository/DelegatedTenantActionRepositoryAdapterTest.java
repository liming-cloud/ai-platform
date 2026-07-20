package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.domain.delegation.DelegatedTenantAction;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionRequest;
import com.aiplatform.identity.domain.delegation.DelegatedTenantActionType;
import com.aiplatform.identity.infrastructure.persistence.converter.DelegatedTenantActionPersistenceConverter;
import com.aiplatform.identity.infrastructure.persistence.entity.DelegatedTenantActionPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.DelegatedTenantActionMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DelegatedTenantActionRepositoryAdapterTest {
    @Test
    void savesTenantScopedActionAndReturnsDatabaseIdentifier() {
        DelegatedTenantActionMapper mapper = mock(DelegatedTenantActionMapper.class);
        DelegatedTenantActionPersistenceConverter converter = mock(DelegatedTenantActionPersistenceConverter.class);
        when(mapper.insertAction(org.mockito.ArgumentMatchers.any())).thenReturn(8101L);
        DelegatedTenantActionRepositoryAdapter adapter =
                new DelegatedTenantActionRepositoryAdapter(mapper, converter);
        DelegatedTenantAction action = action();

        DelegatedTenantAction saved = adapter.save(action);

        assertThat(saved.actionId()).isEqualTo("8101");
        ArgumentCaptor<DelegatedTenantActionPersistenceEntity> row =
                ArgumentCaptor.forClass(DelegatedTenantActionPersistenceEntity.class);
        verify(mapper).insertAction(row.capture());
        assertThat(row.getValue().getTenantId()).isEqualTo(9001L);
        assertThat(row.getValue().getOperatorId()).isEqualTo(1001L);
        assertThat(row.getValue().getReason()).isEqualTo("应急修复错误授权");
        assertThat(row.getValue().getAuditEventId()).isEqualTo("AUDIT-001");
    }

    @Test
    void findsIdempotentActionUsingTenantOperatorAndRequestHash() {
        DelegatedTenantActionMapper mapper = mock(DelegatedTenantActionMapper.class);
        DelegatedTenantActionPersistenceConverter converter = mock(DelegatedTenantActionPersistenceConverter.class);
        DelegatedTenantActionPersistenceEntity row = new DelegatedTenantActionPersistenceEntity();
        DelegatedTenantAction expected = action().withActionId("8101");
        when(mapper.findByRequestHash(9001L, 1001L, "hash-001")).thenReturn(row);
        when(converter.toDomain(row)).thenReturn(expected);
        DelegatedTenantActionRepositoryAdapter adapter =
                new DelegatedTenantActionRepositoryAdapter(mapper, converter);

        assertThat(adapter.findByRequestHash(TenantId.of("9001"), "1001", "hash-001"))
                .contains(expected);
    }

    @Test
    void returnsExistingActionWhenConcurrentInsertLosesUniqueConstraintRace() {
        DelegatedTenantActionMapper mapper = mock(DelegatedTenantActionMapper.class);
        DelegatedTenantActionPersistenceConverter converter = mock(DelegatedTenantActionPersistenceConverter.class);
        DelegatedTenantActionPersistenceEntity row = new DelegatedTenantActionPersistenceEntity();
        DelegatedTenantAction existing = action().withActionId("8101");
        when(mapper.insertAction(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(mapper.findByRequestHash(9001L, 1001L, "hash-001")).thenReturn(row);
        when(converter.toDomain(row)).thenReturn(existing);
        DelegatedTenantActionRepositoryAdapter adapter =
                new DelegatedTenantActionRepositoryAdapter(mapper, converter);

        assertThat(adapter.save(action())).isEqualTo(existing);
    }

    private static DelegatedTenantAction action() {
        DelegatedTenantActionRequest request = new DelegatedTenantActionRequest(
                DelegatedTenantActionType.REPLACE_ROLE_PERMISSIONS,
                "3002",
                null,
                List.of("knowledge-base:knowledge-base:read"),
                "应急修复错误授权",
                "request-001"
        );
        return DelegatedTenantAction.succeeded(
                TenantId.of("9001"),
                "1001",
                request,
                "hash-001",
                "AUDIT-001",
                Instant.parse("2026-07-10T03:00:00Z")
        );
    }
}
