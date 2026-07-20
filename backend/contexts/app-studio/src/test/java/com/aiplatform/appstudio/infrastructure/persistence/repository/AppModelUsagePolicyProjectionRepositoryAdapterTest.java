package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.infrastructure.persistence.converter.AppModelUsagePolicyProjectionPersistenceConverter;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppModelUsagePolicyProjectionPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppModelUsagePolicyProjectionMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppModelUsagePolicyProjectionRepositoryAdapterTest {
    private static final Instant CREATED_AT = Instant.parse("2026-05-25T08:00:00Z");
    private static final String POLICY_DIGEST = "a".repeat(64);

    @Test
    void staleEventCannotOverwriteCurrentProjection() {
        AppModelUsagePolicyProjectionMapper mapper = mock(AppModelUsagePolicyProjectionMapper.class);
        AppModelUsagePolicyProjectionPersistenceEntity current = row(
                "event-new", 3, Instant.parse("2026-05-25T08:10:00Z")
        );
        when(mapper.selectOne(anyQuery())).thenReturn(current);

        AppModelUsagePolicyProjection saved = repository(mapper).save(projection(
                "event-old", Instant.parse("2026-05-25T08:05:00Z")
        ));

        assertThat(saved.sourceEventId()).isEqualTo("event-new");
        verify(mapper, never()).insert(any(AppModelUsagePolicyProjectionPersistenceEntity.class));
        verify(mapper, never()).update(any(AppModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate());
    }

    @Test
    void newerEventPreservesCreationTimeAndAdvancesOptimisticVersion() {
        AppModelUsagePolicyProjectionMapper mapper = mock(AppModelUsagePolicyProjectionMapper.class);
        AppModelUsagePolicyProjectionPersistenceEntity existing = row(
                "event-old", 3, Instant.parse("2026-05-25T08:05:00Z")
        );
        AppModelUsagePolicyProjectionPersistenceEntity updated = row(
                "event-new", 4, Instant.parse("2026-05-25T08:10:00Z")
        );
        when(mapper.selectOne(anyQuery())).thenReturn(existing, updated);
        when(mapper.update(any(AppModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate())).thenReturn(1);

        repository(mapper).save(projection("event-new", Instant.parse("2026-05-25T08:10:00Z")));

        ArgumentCaptor<AppModelUsagePolicyProjectionPersistenceEntity> entityCaptor =
                ArgumentCaptor.forClass(AppModelUsagePolicyProjectionPersistenceEntity.class);
        verify(mapper).update(entityCaptor.capture(), anyUpdate());
        assertThat(entityCaptor.getValue().getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entityCaptor.getValue().getVersion()).isEqualTo(4);
    }

    @Test
    void concurrentNewerProjectionWinsWithoutRetryingOlderPayload() {
        AppModelUsagePolicyProjectionMapper mapper = mock(AppModelUsagePolicyProjectionMapper.class);
        when(mapper.selectOne(anyQuery())).thenReturn(
                row("event-current", 3, Instant.parse("2026-05-25T08:05:00Z")),
                row("event-winner", 4, Instant.parse("2026-05-25T08:11:00Z"))
        );
        when(mapper.update(any(AppModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate())).thenReturn(0);

        AppModelUsagePolicyProjection saved = repository(mapper).save(projection(
                "event-loser", Instant.parse("2026-05-25T08:10:00Z")
        ));

        assertThat(saved.sourceEventId()).isEqualTo("event-winner");
    }

    @Test
    void failedConcurrentUpdateWithoutNewerProjectionFailsClosed() {
        AppModelUsagePolicyProjectionMapper mapper = mock(AppModelUsagePolicyProjectionMapper.class);
        AppModelUsagePolicyProjectionPersistenceEntity existing = row(
                "event-current", 3, Instant.parse("2026-05-25T08:05:00Z")
        );
        when(mapper.selectOne(anyQuery())).thenReturn(existing);
        when(mapper.update(any(AppModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate())).thenReturn(0);

        assertThatThrownBy(() -> repository(mapper).save(projection(
                "event-new", Instant.parse("2026-05-25T08:10:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 应用模型策略投影更新失败");
    }

    @Test
    void equalRevisionWithDifferentDigestFailsClosedInsteadOfTrustingTimestamp() {
        AppModelUsagePolicyProjectionMapper mapper = mock(AppModelUsagePolicyProjectionMapper.class);
        AppModelUsagePolicyProjectionPersistenceEntity existing = row(
                "event-new", 3, Instant.parse("2026-05-25T08:10:00Z")
        );
        existing.setPolicyDigest("b".repeat(64));
        when(mapper.selectOne(anyQuery())).thenReturn(existing);

        assertThatThrownBy(() -> repository(mapper).save(projection(
                "event-new", Instant.parse("2026-05-25T08:10:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("同一修订号载荷摘要冲突");
        verify(mapper, never()).update(any(AppModelUsagePolicyProjectionPersistenceEntity.class), anyUpdate());
    }

    private static AppModelUsagePolicyProjectionRepositoryAdapter repository(
            AppModelUsagePolicyProjectionMapper mapper
    ) {
        return new AppModelUsagePolicyProjectionRepositoryAdapter(
                mapper,
                new AppModelUsagePolicyProjectionPersistenceConverter(new JsonMapper())
        );
    }

    private static AppModelUsagePolicyProjection projection(String eventId, Instant lastEventAt) {
        return new AppModelUsagePolicyProjection(
                TenantId.of("2001"),
                "4001",
                "3001",
                "silicon-flow",
                "openai-compatible",
                new AppModelRuntimeConnectionRef("https://api.siliconflow.cn/v1", "api_key", "secret-9001"),
                "deepseek-chat",
                7,
                "CHAT",
                Set.of("CHAT"),
                Set.of("RAG_QUERY"),
                Set.of(),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                4096,
                2048,
                "policy-" + eventId,
                policyRevision(eventId),
                POLICY_DIGEST,
                null,
                true,
                eventId,
                1,
                lastEventAt
        );
    }

    private static AppModelUsagePolicyProjectionPersistenceEntity row(
            String eventId,
            int version,
            Instant lastEventAt
    ) {
        AppModelUsagePolicyProjectionPersistenceEntity row = new AppModelUsagePolicyProjectionPersistenceEntity();
        row.setId(9001L);
        row.setTenantId(2001L);
        row.setModelConfigId(4001L);
        row.setProviderId(3001L);
        row.setProviderCode("silicon-flow");
        row.setProviderKind("openai-compatible");
        row.setProviderEndpoint("https://api.siliconflow.cn/v1");
        row.setAuthType("api_key");
        row.setSecretRefId("secret-9001");
        row.setModelCode("deepseek-chat");
        row.setModelConfigVersion(7);
        row.setModelType("CHAT");
        row.setCapabilities("CHAT");
        row.setAllowedUseCases("RAG_QUERY");
        row.setAllowedApplicationIds("");
        row.setEffectiveOptionsJson("{\"temperature\":0.2}");
        row.setEffectiveOptionsDigest("a".repeat(64));
        row.setMaxInputTokens(4096);
        row.setMaxOutputTokens(2048);
        row.setPolicyVersion("policy-" + eventId);
        row.setPolicyRevision(policyRevision(eventId));
        row.setPolicyDigest(POLICY_DIGEST);
        row.setEnabled(true);
        row.setSourceEventId(eventId);
        row.setSourceEventVersion(1L);
        row.setLastEventAt(lastEventAt);
        row.setCreatedAt(CREATED_AT);
        row.setUpdatedAt(lastEventAt);
        row.setVersion(version);
        return row;
    }

    private static long policyRevision(String eventId) {
        return switch (eventId) {
            case "event-old", "event-current" -> 1L;
            case "event-new", "event-loser" -> 2L;
            case "event-winner" -> 3L;
            default -> throw new IllegalArgumentException("未知测试事件：" + eventId);
        };
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<AppModelUsagePolicyProjectionPersistenceEntity> anyQuery() {
        return any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<AppModelUsagePolicyProjectionPersistenceEntity> anyUpdate() {
        return any(Wrapper.class);
    }
}
