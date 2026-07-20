package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.infrastructure.persistence.converter.AppPublishPreflightPersistenceConverter;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppPublishPreflightPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppPublishPreflightMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class AppPublishPreflightRepositoryAdapterTest {
    @Test
    void saveReturnsTheCanonicalPersistedResultWithItsCandidateFingerprint() {
        AppPublishPreflightMapper mapper = Mockito.mock(AppPublishPreflightMapper.class);
        AppPublishPreflightPersistenceConverter converter =
                new AppPublishPreflightPersistenceConverter(new JsonMapper());
        AppPublishPreflightRepositoryAdapter repository =
                new AppPublishPreflightRepositoryAdapter(mapper, converter);
        AtomicReference<AppPublishPreflightPersistenceEntity> inserted = new AtomicReference<>();
        when(mapper.insertResult(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.findByRequestId(2001L, 3001L, "publish-1"))
                .thenAnswer(ignored -> inserted.get());
        AppPublishPreflightResult result = new AppPublishPreflightResult(
                TenantId.of("2001"),
                "3001",
                "publish-1",
                "b".repeat(64),
                true,
                List.of(),
                List.of(),
                "trace-1",
                "1001",
                Instant.parse("2026-07-18T10:00:00Z")
        );

        AppPublishPreflightResult saved = repository.save(result);

        assertThat(saved).isEqualTo(result);
        assertThat(inserted.get().getCandidateFingerprint()).isEqualTo("b".repeat(64));
        verify(mapper).findByRequestId(2001L, 3001L, "publish-1");
    }
}
