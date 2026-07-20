package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.infrastructure.persistence.mapper.ModelUsagePolicyEventReceiptMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyEventReceiptRepositoryAdapterTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-17T08:00:00Z");

    @Test
    void returnsTrueOnlyForTheFirstPersistedEventId() {
        ModelUsagePolicyEventReceiptMapper mapper = mock(ModelUsagePolicyEventReceiptMapper.class);
        when(mapper.insertIfAbsent("event-1", 1, OCCURRED_AT)).thenReturn(1, 0);
        ModelUsagePolicyEventReceiptRepositoryAdapter repository =
                new ModelUsagePolicyEventReceiptRepositoryAdapter(mapper);

        assertThat(repository.recordIfAbsent("event-1", 1, OCCURRED_AT)).isTrue();
        assertThat(repository.recordIfAbsent("event-1", 1, OCCURRED_AT)).isFalse();
    }

    @Test
    void failsClosedForAnInvalidMapperRowCount() {
        ModelUsagePolicyEventReceiptMapper mapper = mock(ModelUsagePolicyEventReceiptMapper.class);
        when(mapper.insertIfAbsent("event-1", 1, OCCURRED_AT)).thenReturn(2);
        ModelUsagePolicyEventReceiptRepositoryAdapter repository =
                new ModelUsagePolicyEventReceiptRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.recordIfAbsent("event-1", 1, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("消费凭据");
    }
}
