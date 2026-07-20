package com.aiplatform.approval.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.approval.infrastructure.messaging.outbox.ApprovalDecisionOutboxDispatcher;
import com.aiplatform.approval.infrastructure.messaging.outbox.ApprovalDecisionOutboxRecord;
import com.aiplatform.approval.infrastructure.persistence.mapper.ApprovalDecisionOutboxMapper;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovalDecisionOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");
    private static final String WORKER_ID = "approval-worker-1";

    @Test
    void publishesClaimedEventsAndMarksOnlyTheOwnedLeaseComplete() {
        ApprovalDecisionOutboxMapper mapper = mock(ApprovalDecisionOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ApprovalDecisionOutboxRecord record = record(1);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 20)).thenReturn(List.of(record));
        when(mapper.markPublished(91L, WORKER_ID, NOW)).thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isEqualTo(1);

        verify(events).publish(record.toDomainEventMessage());
        verify(mapper).markPublished(91L, WORKER_ID, NOW);
        verify(mapper, never()).markRetry(anyLong(), any(), any(), any(), any());
    }

    @Test
    void brokerFailureReturnsTheLeaseToPendingWithoutLeakingFailureText() {
        ApprovalDecisionOutboxMapper mapper = mock(ApprovalDecisionOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ApprovalDecisionOutboxRecord record = record(2);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 20)).thenReturn(List.of(record));
        doThrow(new IllegalStateException("broker rejected sk-must-not-leak"))
                .when(events).publish(any(DomainEventMessage.class));
        when(mapper.markRetry(91L, WORKER_ID, NOW, NOW.plusSeconds(10), "IllegalStateException"))
                .thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isZero();

        verify(mapper).markRetry(91L, WORKER_ID, NOW, NOW.plusSeconds(10), "IllegalStateException");
        verify(mapper, never()).markPublished(anyLong(), any(), any());
    }

    @Test
    void sendAcceptedThenCompletionFailureSchedulesSafeDuplicateDelivery() {
        ApprovalDecisionOutboxMapper mapper = mock(ApprovalDecisionOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ApprovalDecisionOutboxRecord record = record(1);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 20)).thenReturn(List.of(record));
        when(mapper.markPublished(91L, WORKER_ID, NOW))
                .thenThrow(new IllegalStateException("database acknowledgement failed"));
        when(mapper.markRetry(91L, WORKER_ID, NOW, NOW.plusSeconds(5), "IllegalStateException"))
                .thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isZero();

        verify(events).publish(record.toDomainEventMessage());
        verify(mapper).markRetry(91L, WORKER_ID, NOW, NOW.plusSeconds(5), "IllegalStateException");
    }

    private static ApprovalDecisionOutboxDispatcher dispatcher(
            ApprovalDecisionOutboxMapper mapper,
            DomainEventPublisher events
    ) {
        return new ApprovalDecisionOutboxDispatcher(
                mapper,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                WORKER_ID,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                20
        );
    }

    private static ApprovalDecisionOutboxRecord record(int attempts) {
        return new ApprovalDecisionOutboxRecord(
                91L,
                "ApprovalDecisionRecorded:2001:7001:APPROVED",
                "ApprovalDecisionRecorded",
                "2001",
                "ApprovalTask",
                "7001",
                1,
                "v1",
                Instant.parse("2026-07-18T07:59:00Z"),
                "trace-approval-7001",
                "approval-center",
                "{\"taskId\":\"7001\"}",
                attempts
        );
    }
}
