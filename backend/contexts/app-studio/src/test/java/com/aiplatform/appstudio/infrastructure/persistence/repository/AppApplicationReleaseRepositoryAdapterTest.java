package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseConflictException;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation;
import com.aiplatform.appstudio.infrastructure.persistence.converter.AppApplicationReleasePersistenceConverter;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationReleasePersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppApplicationReleaseMapper;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.ApplicationReleaseOutboxMapper;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

class AppApplicationReleaseRepositoryAdapterTest {
    private static final TenantId TENANT = TenantId.of("1");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-18T08:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-18T08:01:00.123456789Z");

    @Test
    void activeCatalogUsesOneBatchQueryForTheBoundedApplicationWindow() {
        AppApplicationReleaseMapper mapper = mock(AppApplicationReleaseMapper.class);
        JsonMapper json = JsonMapper.builder().build();
        AppApplicationReleasePersistenceConverter converter = new AppApplicationReleasePersistenceConverter(json);
        AppApplicationReleaseRepositoryAdapter repository = new AppApplicationReleaseRepositoryAdapter(
                mapper,
                mock(ApplicationReleaseOutboxMapper.class),
                converter,
                json
        );
        when(mapper.findActiveByApplicationIds(1L, List.of(3001L)))
                .thenReturn(List.of(converter.fromCommand(7001L, 1L, command())));

        Map<String, com.aiplatform.appstudio.domain.release.AiApplicationRelease> releases =
                repository.findActiveByApplicationIds(TENANT, List.of("3001"));

        assertThat(releases).containsOnlyKeys("3001");
        assertThat(releases.get("3001").releaseId()).isEqualTo("7001");
        verify(mapper).findActiveByApplicationIds(1L, List.of(3001L));
        verify(mapper, never()).findActive(any(Long.class), any(Long.class));
    }

    @Test
    void rowLockSerializesVersionPointerStatusAndOutboxMutation() {
        AppApplicationReleaseMapper mapper = mock(AppApplicationReleaseMapper.class);
        ApplicationReleaseOutboxMapper outbox = mock(ApplicationReleaseOutboxMapper.class);
        JsonMapper json = JsonMapper.builder().build();
        AppApplicationReleasePersistenceConverter converter =
                new AppApplicationReleasePersistenceConverter(json);
        AppApplicationReleaseRepositoryAdapter repository = new AppApplicationReleaseRepositoryAdapter(
                mapper, outbox, converter, json
        );
        when(mapper.lockApplication(1, 3001)).thenReturn(UPDATED_AT);
        when(mapper.nextReleaseId()).thenReturn(7001L);
        when(mapper.nextVersion(1, 3001)).thenReturn(1L);
        when(mapper.insertRelease(any())).thenReturn(1);
        when(mapper.activateRelease(1, 3001, 7001, 1001, PUBLISHED_AT)).thenReturn(1);
        when(mapper.markApplicationPublished(1, 3001, 1001, PUBLISHED_AT)).thenReturn(1);
        Instant persistedPublishedAt = Instant.parse("2026-07-18T08:01:00.123457Z");
        AppApplicationReleasePersistenceEntity persisted = converter.fromCommand(7001L, 1L, command());
        persisted.setPublishedAt(persistedPublishedAt);
        when(mapper.findById(1, 3001, 7001)).thenReturn(persisted);
        when(outbox.enqueue(any())).thenReturn(1);

        ApplicationReleaseRepository.MutationResult result = repository.createAndActivate(command());

        assertThat(result.created()).isTrue();
        assertThat(result.release().releaseId()).isEqualTo("7001");
        assertThat(result.release().version()).isEqualTo(1);
        assertThat(result.release().publishedAt()).isEqualTo(persistedPublishedAt);
        assertThat(result.release().snapshot().updatedAt()).isEqualTo(persistedPublishedAt);
        assertThat(result.release().allowedUserIds()).containsExactlyInAnyOrder("1001", "1002");
        InOrder order = inOrder(mapper, outbox);
        order.verify(mapper).lockApplication(1, 3001);
        order.verify(mapper).findByRequestId(1, 3001, "publish-request-1");
        order.verify(mapper).nextReleaseId();
        order.verify(mapper).nextVersion(1, 3001);
        order.verify(mapper).insertRelease(any());
        order.verify(mapper).activateRelease(1, 3001, 7001, 1001, PUBLISHED_AT);
        order.verify(mapper).markApplicationPublished(1, 3001, 1001, PUBLISHED_AT);
        order.verify(mapper).findById(1, 3001, 7001);
        order.verify(outbox).enqueue(any());

        ArgumentCaptor<DomainEventMessage> event = ArgumentCaptor.forClass(DomainEventMessage.class);
        verify(outbox).enqueue(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("ApplicationReleasePublished");
        assertThat(event.getValue().version()).isEqualTo(1);
        assertThat(event.getValue().payloadJson())
                .contains("\"releaseId\":\"7001\"")
                .doesNotContain("系统提示词", "prompt-v1");
    }

    @Test
    void duplicateRequestReturnsCanonicalReleaseWithoutASecondMutationOrOutbox() {
        AppApplicationReleaseMapper mapper = mock(AppApplicationReleaseMapper.class);
        ApplicationReleaseOutboxMapper outbox = mock(ApplicationReleaseOutboxMapper.class);
        JsonMapper json = JsonMapper.builder().build();
        AppApplicationReleasePersistenceConverter converter =
                new AppApplicationReleasePersistenceConverter(json);
        AppApplicationReleaseRepositoryAdapter repository = new AppApplicationReleaseRepositoryAdapter(
                mapper, outbox, converter, json
        );
        AppApplicationReleasePersistenceEntity existing = converter.fromCommand(7001, 1, command());
        when(mapper.lockApplication(1, 3001)).thenReturn(UPDATED_AT.plusSeconds(30));
        when(mapper.findByRequestId(1, 3001, "publish-request-1")).thenReturn(existing);

        ApplicationReleaseRepository.MutationResult result = repository.createAndActivate(command());

        assertThat(result.created()).isFalse();
        assertThat(result.release().releaseId()).isEqualTo("7001");
        verify(mapper, never()).insertRelease(any());
        verify(mapper, never()).activateRelease(any(Long.class), any(Long.class), any(Long.class), any(Long.class), any());
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void duplicateRequestWithDifferentOperationIsRejectedWithoutMutation() {
        AppApplicationReleaseMapper mapper = mock(AppApplicationReleaseMapper.class);
        ApplicationReleaseOutboxMapper outbox = mock(ApplicationReleaseOutboxMapper.class);
        JsonMapper json = JsonMapper.builder().build();
        AppApplicationReleasePersistenceConverter converter =
                new AppApplicationReleasePersistenceConverter(json);
        AppApplicationReleaseRepositoryAdapter repository = new AppApplicationReleaseRepositoryAdapter(
                mapper,
                outbox,
                converter,
                json
        );
        AppApplicationReleasePersistenceEntity existing = converter.fromCommand(7001, 1, command());
        when(mapper.lockApplication(1, 3001)).thenReturn(UPDATED_AT.plusSeconds(30));
        when(mapper.findByRequestId(1, 3001, "publish-request-1")).thenReturn(existing);

        assertThatThrownBy(() -> repository.createAndActivate(rollbackCommand()))
                .isInstanceOf(ApplicationReleaseConflictException.class)
                .hasMessageContaining("幂等请求 ID");
        verify(mapper, never()).insertRelease(any());
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void stalePreflightFailsClosedAfterLockAndWritesNothing() {
        AppApplicationReleaseMapper mapper = mock(AppApplicationReleaseMapper.class);
        ApplicationReleaseOutboxMapper outbox = mock(ApplicationReleaseOutboxMapper.class);
        JsonMapper json = JsonMapper.builder().build();
        AppApplicationReleasePersistenceConverter converter =
                new AppApplicationReleasePersistenceConverter(json);
        AppApplicationReleaseRepositoryAdapter repository = new AppApplicationReleaseRepositoryAdapter(
                mapper,
                outbox,
                converter,
                json
        );
        when(mapper.lockApplication(1, 3001)).thenReturn(UPDATED_AT.plusSeconds(1));

        assertThatThrownBy(() -> repository.createAndActivate(command()))
                .isInstanceOf(ApplicationReleaseConflictException.class)
                .hasMessageContaining("重新执行发布预检");
        verify(mapper, never()).insertRelease(any());
        verify(outbox, never()).enqueue(any());
    }

    @Test
    void outboxWriteFailureAbortsTheTransactionalMutation() {
        AppApplicationReleaseMapper mapper = mock(AppApplicationReleaseMapper.class);
        ApplicationReleaseOutboxMapper outbox = mock(ApplicationReleaseOutboxMapper.class);
        JsonMapper json = JsonMapper.builder().build();
        AppApplicationReleasePersistenceConverter converter =
                new AppApplicationReleasePersistenceConverter(json);
        AppApplicationReleaseRepositoryAdapter repository = new AppApplicationReleaseRepositoryAdapter(
                mapper,
                outbox,
                converter,
                json
        );
        when(mapper.lockApplication(1, 3001)).thenReturn(UPDATED_AT);
        when(mapper.nextReleaseId()).thenReturn(7001L);
        when(mapper.nextVersion(1, 3001)).thenReturn(1L);
        when(mapper.insertRelease(any())).thenReturn(1);
        when(mapper.activateRelease(1, 3001, 7001, 1001, PUBLISHED_AT)).thenReturn(1);
        when(mapper.markApplicationPublished(1, 3001, 1001, PUBLISHED_AT)).thenReturn(1);
        when(mapper.findById(1, 3001, 7001)).thenReturn(converter.fromCommand(7001L, 1L, command()));
        when(outbox.enqueue(any())).thenReturn(0);

        assertThatThrownBy(() -> repository.createAndActivate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox 写入失败");
    }

    private static ApplicationReleaseRepository.MutationCommand command() {
        AiApplication snapshot = new AiApplication(
                TENANT,
                "3001",
                AiApplicationType.KNOWLEDGE_QA,
                "制度助手",
                "制度问答",
                "9001",
                List.of("20"),
                List.of(),
                Set.of("1001"),
                "prompt-v1",
                ApplicationPublicationStatus.PUBLISHED,
                UPDATED_AT.minusSeconds(3600),
                PUBLISHED_AT
        );
        return new ApplicationReleaseRepository.MutationCommand(
                TENANT,
                "3001",
                "publish-request-1",
                ApplicationReleaseOperation.PUBLISH,
                null,
                snapshot,
                Set.of("1001", "1002"),
                "1001",
                "首次上线",
                "trace-publish-1",
                "event-publish-1",
                PUBLISHED_AT,
                UPDATED_AT
        );
    }

    private static ApplicationReleaseRepository.MutationCommand rollbackCommand() {
        ApplicationReleaseRepository.MutationCommand publish = command();
        return new ApplicationReleaseRepository.MutationCommand(
                publish.tenantId(),
                publish.applicationId(),
                publish.requestId(),
                ApplicationReleaseOperation.ROLLBACK,
                "6999",
                publish.snapshot(),
                publish.allowedUserIds(),
                publish.actorId(),
                "回滚第一版",
                publish.traceId(),
                publish.eventId(),
                publish.occurredAt(),
                publish.expectedApplicationUpdatedAt()
        );
    }
}
