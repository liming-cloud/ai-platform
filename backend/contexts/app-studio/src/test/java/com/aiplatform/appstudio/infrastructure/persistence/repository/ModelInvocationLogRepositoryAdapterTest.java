package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.port.chat.model.ModelInvocationAuditRecord;
import com.aiplatform.appstudio.infrastructure.persistence.entity.ModelInvocationLogPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.ModelInvocationLogMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ModelInvocationLogRepositoryAdapterTest {
    @Test
    void recordPersistsInvocationAuditAndReturnsGeneratedId() {
        ModelInvocationLogMapper mapper = Mockito.mock(ModelInvocationLogMapper.class);
        when(mapper.insert(any(ModelInvocationLogPersistenceEntity.class))).thenAnswer(invocation -> {
            ModelInvocationLogPersistenceEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            return 1;
        });
        ModelInvocationLogRepositoryAdapter adapter = new ModelInvocationLogRepositoryAdapter(mapper);

        String id = adapter.record(new ModelInvocationAuditRecord(
                TenantId.of("2001"),
                "trace-1",
                "client-message-1",
                "APP_STUDIO",
                "AI_APPLICATION",
                "3001",
                "6",
                "hash-1",
                100,
                20,
                350,
                "SUCCEEDED",
                null,
                null,
                Instant.parse("2026-06-16T00:00:00Z")
        ));

        assertThat(id).isEqualTo("88");
        ArgumentCaptor<ModelInvocationLogPersistenceEntity> captor = ArgumentCaptor.forClass(ModelInvocationLogPersistenceEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-1");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("client-message-1");
        assertThat(captor.getValue().getSourceContext()).isEqualTo("APP_STUDIO");
        assertThat(captor.getValue().getSourceResourceType()).isEqualTo("AI_APPLICATION");
        assertThat(captor.getValue().getSourceResourceId()).isEqualTo(3001L);
        assertThat(captor.getValue().getModelConfigId()).isEqualTo(6L);
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCEEDED");
    }
}
