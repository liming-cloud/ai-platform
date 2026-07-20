package com.aiplatform.appstudio.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.domain.port.modelpolicy.AppModelUsagePolicyProjectionRepository;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatabaseAppModelUsagePolicyAdapterTest {
    private static final String POLICY_DIGEST = "a".repeat(64);

    @Test
    void deniesWhenLocalProjectionIsMissing() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        when(repository.findByModelConfigId(TenantId.of("2001"), "4001")).thenReturn(Optional.empty());
        DatabaseAppModelUsagePolicyAdapter adapter = new DatabaseAppModelUsagePolicyAdapter(repository, Clock.systemUTC());

        AppModelUsageDecision decision = adapter.decide(request());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("model_policy_projection_missing");
    }

    @Test
    void allowsWhenLocalProjectionIsEnabled() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        when(repository.findByModelConfigId(TenantId.of("2001"), "4001")).thenReturn(Optional.of(projection()));
        DatabaseAppModelUsagePolicyAdapter adapter = new DatabaseAppModelUsagePolicyAdapter(
                repository,
                Clock.fixed(Instant.parse("2026-05-25T08:00:00Z"), ZoneOffset.UTC)
        );

        AppModelUsageDecision decision = adapter.decide(request());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.runtimeDescriptor().providerCode()).isEqualTo("silicon-flow");
        assertThat(decision.runtimeDescriptor().connectionRef().secretRefId()).isEqualTo("secret-9001");
    }

    @Test
    void deniesWhenApplicationScopeDoesNotContainRequestApplication() {
        AppModelUsagePolicyProjectionRepository repository = mock(AppModelUsagePolicyProjectionRepository.class);
        when(repository.findByModelConfigId(TenantId.of("2001"), "4001"))
                .thenReturn(Optional.of(scopedProjection("9009")));
        DatabaseAppModelUsagePolicyAdapter adapter = new DatabaseAppModelUsagePolicyAdapter(repository, Clock.systemUTC());

        AppModelUsageDecision decision = adapter.decide(request());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("application_not_allowed");
    }

    private static AppModelUsageRequest request() {
        return new AppModelUsageRequest(
                TenantId.of("2001"),
                "1001",
                "user",
                Set.of("app-user"),
                Set.of(new PermissionCode("app-studio:model:use")),
                "7001",
                "RAG_QUERY",
                "CHAT",
                "4001",
                Map.of(),
                20,
                512
        );
    }

    private static AppModelUsagePolicyProjection projection() {
        return scopedProjection();
    }

    private static AppModelUsagePolicyProjection scopedProjection(String... allowedApplicationIds) {
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
                Set.of(allowedApplicationIds),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                4096,
                2048,
                "v1",
                1L,
                POLICY_DIGEST,
                null,
                true,
                "event-001",
                1,
                Instant.parse("2026-05-25T07:00:00Z")
        );
    }
}
