package com.aiplatform.modelhub.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelApprovalRequestOutboxDispatcher;
import com.aiplatform.modelhub.infrastructure.messaging.outbox.ModelApprovalRequestOutboxRecord;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelApprovalRequestOutboxMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelApprovalRequestOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");
    private static final String WORKER_ID = "approval-request-worker-1";

    @Test
    void publishesClaimedRequestAndMarksOnlyOwnedLeaseComplete() {
        ModelApprovalRequestOutboxMapper mapper = mock(ModelApprovalRequestOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ModelApprovalRequestOutboxRecord record = record(1);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 20)).thenReturn(List.of(record));
        when(mapper.markPublished(91L, WORKER_ID, NOW)).thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isEqualTo(1);

        verify(events).publish(record.toDomainEventMessage());
        verify(mapper).markPublished(91L, WORKER_ID, NOW);
        verify(mapper, never()).markRetry(anyLong(), any(), any(), any(), any());
    }

    @Test
    void brokerFailureReturnsLeaseToPendingWithSanitizedBackoff() {
        ModelApprovalRequestOutboxMapper mapper = mock(ModelApprovalRequestOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ModelApprovalRequestOutboxRecord record = record(2);
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
    void acceptedSendThenCompletionFailureSchedulesSameEnvelopeForRedelivery() {
        ModelApprovalRequestOutboxMapper mapper = mock(ModelApprovalRequestOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ModelApprovalRequestOutboxRecord record = record(1);
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 20)).thenReturn(List.of(record));
        when(mapper.markPublished(91L, WORKER_ID, NOW))
                .thenThrow(new IllegalStateException("database acknowledgement failed"));
        when(mapper.markRetry(91L, WORKER_ID, NOW, NOW.plusSeconds(5), "IllegalStateException"))
                .thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isZero();

        verify(events).publish(record.toDomainEventMessage());
        verify(mapper).markRetry(91L, WORKER_ID, NOW, NOW.plusSeconds(5), "IllegalStateException");
    }

    @Test
    void publishesCanonicalCancellationAfterPostgresMicrosecondRoundTrip() {
        ModelApprovalRequestOutboxMapper mapper = mock(ModelApprovalRequestOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        Instant sourceClock = Instant.parse("2026-07-20T07:59:00.123456789Z");
        Instant persistedAt = sourceClock.truncatedTo(ChronoUnit.MICROS);
        ModelApprovalRequestOutboxRecord record = new ModelApprovalRequestOutboxRecord(
                91L,
                "ModelApprovalCancellationRequested:2001:model_provider:REQ-91",
                "ModelApprovalCancellationRequested",
                "2001",
                "ModelProvider",
                "3001",
                1,
                "v1",
                persistedAt,
                "trace-approval-cancellation-91",
                "model-hub",
                "{\"approvalRequestId\":\"REQ-91\",\"requestedAt\":\"%s\"}".formatted(persistedAt),
                1
        );
        when(mapper.claimBatch(WORKER_ID, NOW, NOW.plusSeconds(30), 20)).thenReturn(List.of(record));
        when(mapper.markPublished(91L, WORKER_ID, NOW)).thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isEqualTo(1);

        ArgumentCaptor<DomainEventMessage> messageCaptor = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(events).publish(messageCaptor.capture());
        assertThat(messageCaptor.getValue().occurredAt()).isEqualTo(persistedAt);
        assertThat(messageCaptor.getValue().payloadJson())
                .contains("\"requestedAt\":\"2026-07-20T07:59:00.123456Z\"");
    }

    private static ModelApprovalRequestOutboxDispatcher dispatcher(
            ModelApprovalRequestOutboxMapper mapper,
            DomainEventPublisher events
    ) {
        return new ModelApprovalRequestOutboxDispatcher(
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

    private static ModelApprovalRequestOutboxRecord record(int attempts) {
        return new ModelApprovalRequestOutboxRecord(
                91L,
                "ModelConfigApprovalRequested:2001:model_config:REQ-91",
                "ModelConfigApprovalRequested",
                "2001",
                "ModelConfig",
                "4001",
                1,
                "v1",
                Instant.parse("2026-07-20T07:59:00Z"),
                "trace-approval-request-91",
                "model-hub",
                "{\"approvalRequestId\":\"REQ-91\"}",
                attempts
        );
    }
}
