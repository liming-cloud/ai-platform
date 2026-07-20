package com.aiplatform.knowledge.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.knowledge.infrastructure.persistence.mapper.KbModelUsagePolicyEventReceiptMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgeModelUsagePolicyEventReceiptRepositoryAdapterTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-17T08:00:00Z");

    @Test
    void returnsTrueOnlyForTheFirstPersistedEventId() {
        KbModelUsagePolicyEventReceiptMapper mapper = mock(KbModelUsagePolicyEventReceiptMapper.class);
        when(mapper.insertIfAbsent("event-1", 1, OCCURRED_AT)).thenReturn(1, 0);
        KnowledgeModelUsagePolicyEventReceiptRepositoryAdapter repository =
                new KnowledgeModelUsagePolicyEventReceiptRepositoryAdapter(mapper);

        assertThat(repository.recordIfAbsent("event-1", 1, OCCURRED_AT)).isTrue();
        assertThat(repository.recordIfAbsent("event-1", 1, OCCURRED_AT)).isFalse();
    }

    @Test
    void failsClosedForAnInvalidMapperRowCount() {
        KbModelUsagePolicyEventReceiptMapper mapper = mock(KbModelUsagePolicyEventReceiptMapper.class);
        when(mapper.insertIfAbsent("event-1", 1, OCCURRED_AT)).thenReturn(2);
        KnowledgeModelUsagePolicyEventReceiptRepositoryAdapter repository =
                new KnowledgeModelUsagePolicyEventReceiptRepositoryAdapter(mapper);

        assertThatThrownBy(() -> repository.recordIfAbsent("event-1", 1, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("消费凭据");
    }
}
