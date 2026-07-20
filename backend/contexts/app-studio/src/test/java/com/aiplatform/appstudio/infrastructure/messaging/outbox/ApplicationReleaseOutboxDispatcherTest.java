package com.aiplatform.appstudio.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.infrastructure.persistence.mapper.ApplicationReleaseOutboxMapper;
import com.aiplatform.infra.rocketmq.publisher.DomainEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationReleaseOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void publishesClaimedEventAndCompletesItWithTheSameLeaseOwner() {
        ApplicationReleaseOutboxMapper mapper = mock(ApplicationReleaseOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ApplicationReleaseOutboxRecord record = record(1L, 1);
        when(mapper.claimBatch("release-worker", NOW, NOW.plusSeconds(30), 20))
                .thenReturn(List.of(record));
        when(mapper.markPublished(1L, "release-worker", NOW)).thenReturn(1);

        int published = dispatcher(mapper, events).dispatchBatch();

        assertThat(published).isEqualTo(1);
        verify(events).publish(record.toDomainEventMessage());
        verify(mapper).markPublished(1L, "release-worker", NOW);
    }

    @Test
    void failedPublishIsReleasedForRetryWithBoundedExponentialBackoff() {
        ApplicationReleaseOutboxMapper mapper = mock(ApplicationReleaseOutboxMapper.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        ApplicationReleaseOutboxRecord record = record(2L, 3);
        when(mapper.claimBatch("release-worker", NOW, NOW.plusSeconds(30), 20))
                .thenReturn(List.of(record));
        doThrow(new IllegalStateException("provider unavailable")).when(events).publish(any());
        when(mapper.markRetry(
                2L,
                "release-worker",
                NOW,
                NOW.plusSeconds(20),
                "IllegalStateException"
        )).thenReturn(1);

        assertThat(dispatcher(mapper, events).dispatchBatch()).isZero();

        verify(mapper).markRetry(
                2L,
                "release-worker",
                NOW,
                NOW.plusSeconds(20),
                "IllegalStateException"
        );
    }

    @Test
    void nullClaimResultFailsClosedInsteadOfSilentlyDroppingWork() {
        ApplicationReleaseOutboxMapper mapper = mock(ApplicationReleaseOutboxMapper.class);
        when(mapper.claimBatch("release-worker", NOW, NOW.plusSeconds(30), 20)).thenReturn(null);

        assertThatThrownBy(() -> dispatcher(mapper, mock(DomainEventPublisher.class)).dispatchBatch())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim");
    }

    private static ApplicationReleaseOutboxDispatcher dispatcher(
            ApplicationReleaseOutboxMapper mapper,
            DomainEventPublisher events
    ) {
        return new ApplicationReleaseOutboxDispatcher(
                mapper,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "release-worker",
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                20
        );
    }

    private static ApplicationReleaseOutboxRecord record(long id, int attempts) {
        ApplicationReleaseOutboxRecord record = new ApplicationReleaseOutboxRecord();
        record.setId(id);
        record.setEventId("release-event-" + id);
        record.setEventType("ApplicationReleasePublished");
        record.setTenantId("1");
        record.setAggregateType("AiApplication");
        record.setAggregateId("3001");
        record.setEventVersion(1L);
        record.setSchemaVersion("v1");
        record.setOccurredAt(NOW);
        record.setTraceId("TRACE-RELEASE");
        record.setProducer("app-studio");
        record.setPayloadJson("{}");
        record.setAttempts(attempts);
        return record;
    }
}
