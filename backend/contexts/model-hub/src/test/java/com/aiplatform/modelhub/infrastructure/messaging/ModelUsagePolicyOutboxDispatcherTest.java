package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelUsagePolicyOutboxDispatcher;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelUsagePolicyOutboxRecord;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelUsagePolicyOutboxMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final String WORKER_ID = "model-policy-worker-1";

    @Test
    void publishesOnlyClaimedRowsThenMarksTheOwnedLeasePublished() {
        ModelUsagePolicyOutboxMapper mapper = mock(ModelUsagePolicyOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ModelUsagePolicyOutboxRecord record = record(1);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 10)).thenReturn(List.of(record));
        when(mapper.markPublished(91L, WORKER_ID, NOW)).thenReturn(1);

        int published = dispatcher(mapper, events).dispatchBatch();

        assertThat(published).isEqualTo(1);
        verify(events).publish(record.toDomainEventMessage());
        verify(mapper).markPublished(91L, WORKER_ID, NOW);
        verify(mapper, never()).markRetry(any(Long.class), any(String.class), any(Instant.class), any(Instant.class), any(String.class));
    }

    @Test
    void failedPublishReturnsTheOwnedLeaseToPendingWithBoundedBackoffAndSanitizedError() {
        ModelUsagePolicyOutboxMapper mapper = mock(ModelUsagePolicyOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ModelUsagePolicyOutboxRecord record = record(3);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 10)).thenReturn(List.of(record));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker rejected sk-live-must-not-leak"))
                .when(events).publish(any(DomainEventMessage.class));
        when(mapper.markRetry(
                91L,
                WORKER_ID,
                NOW,
                NOW.plusSeconds(40),
                "IllegalStateException"
        )).thenReturn(1);

        int published = dispatcher(mapper, events).dispatchBatch();

        assertThat(published).isZero();
        verify(mapper).markRetry(
                91L,
                WORKER_ID,
                NOW,
                NOW.plusSeconds(40),
                "IllegalStateException"
        );
        verify(mapper, never()).markPublished(any(Long.class), any(String.class), any(Instant.class));
    }

    @Test
    void emptyClaimNeverCallsTheMessageBroker() {
        ModelUsagePolicyOutboxMapper mapper = mock(ModelUsagePolicyOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 10)).thenReturn(List.of());

        assertThat(dispatcher(mapper, events).dispatchBatch()).isZero();

        verify(events, never()).publish(any(DomainEventMessage.class));
    }

    private static ModelUsagePolicyOutboxDispatcher dispatcher(
            ModelUsagePolicyOutboxMapper mapper,
            DomainEventPublisher events
    ) {
        return new ModelUsagePolicyOutboxDispatcher(
                mapper,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                WORKER_ID,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                Duration.ofMinutes(5),
                10
        );
    }

    private static ModelUsagePolicyOutboxRecord record(int attempts) {
        return new ModelUsagePolicyOutboxRecord(
                91L,
                "event-model-policy-91",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4001",
                1,
                "v1",
                Instant.parse("2026-07-17T07:59:00Z"),
                "trace-model-policy-91",
                "model-hub",
                "{\"modelConfigId\":\"4001\"}",
                attempts
        );
    }
}
