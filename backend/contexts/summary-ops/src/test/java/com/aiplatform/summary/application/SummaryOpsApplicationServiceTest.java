package com.aiplatform.summary.application;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_APP_PUBLISH_BLOCKS;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_APPROVAL_TODOS;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_MODEL_HEALTH;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_RAG_SECURITY;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_TENANT_HEALTH;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_APP_HEALTH;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_ENABLED_FEATURES;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_KNOWLEDGE_HEALTH;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_PERMISSION_TODOS;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_USER_ROLE_GAPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricProvider;
import com.aiplatform.contracts.capability.summary.SummaryMetricQuery;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.contracts.capability.summary.SummaryMetricValue;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.feature.TenantFeatureGuard;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.summary.domain.SummaryMetricSnapshot;
import com.aiplatform.summary.domain.port.SummaryMetricSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SummaryOpsApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");

    @Test
    void platformOverviewRejectsActorWithoutPlatformPermission() {
        SummaryOpsApplicationService service = service(List.of(), new InMemorySnapshotRepository(), Set.of());

        assertThatThrownBy(() -> service.platformOverview(
                TenantId.of("2001"), "1003", null, null, "trace-denied"
        )).isInstanceOf(ApplicationException.class);
    }

    @Test
    void failedMetricSourceReturnsPartialResponseWithSnapshotAndTraceId() {
        InMemorySnapshotRepository snapshots = new InMemorySnapshotRepository();
        snapshots.save(new SummaryMetricSnapshot(
                "0",
                SummaryMetricScope.PLATFORM,
                PLATFORM_MODEL_HEALTH,
                Map.of("healthy", 7L, "failed", 1L),
                SummaryMetricFreshness.FIVE_MINUTES,
                false,
                null,
                NOW.minusSeconds(120)
        ));
        SummaryMetricProvider stable = provider(
                "stable",
                SummaryMetricScope.PLATFORM,
                Set.of(
                        PLATFORM_TENANT_HEALTH,
                        PLATFORM_APPROVAL_TODOS,
                        PLATFORM_RAG_SECURITY,
                        PLATFORM_APP_PUBLISH_BLOCKS
                ),
                query -> List.of(
                        value(PLATFORM_TENANT_HEALTH, query),
                        value(PLATFORM_APPROVAL_TODOS, query),
                        value(PLATFORM_RAG_SECURITY, query),
                        value(PLATFORM_APP_PUBLISH_BLOCKS, query)
                )
        );
        SummaryMetricProvider failed = provider(
                "failed-model",
                SummaryMetricScope.PLATFORM,
                Set.of(PLATFORM_MODEL_HEALTH),
                query -> { throw new IllegalStateException("model source unavailable"); }
        );
        SummaryOpsApplicationService service = service(
                List.of(stable, failed),
                snapshots,
                Set.of(new PermissionCode(SummaryOpsApplicationService.PLATFORM_PERMISSION))
        );

        var result = service.platformOverview(
                TenantId.of("2001"), "1001", null, null, "trace-partial"
        );

        assertThat(result.partial()).isTrue();
        assertThat(result.failedMetrics()).containsExactly(PLATFORM_MODEL_HEALTH);
        assertThat(result.traceId()).isEqualTo("trace-partial");
        assertThat(result.modelHealth()).containsEntry("healthy", 7L);
        assertThat(result.freshness()).isEqualTo("FIVE_MINUTES");
    }

    @Test
    void tenantWorkbenchQueriesOnlyCurrentTenantScope() {
        List<SummaryMetricQuery> received = new ArrayList<>();
        Set<String> metrics = Set.of(
                TENANT_ENABLED_FEATURES,
                TENANT_USER_ROLE_GAPS,
                TENANT_KNOWLEDGE_HEALTH,
                TENANT_APP_HEALTH,
                TENANT_PERMISSION_TODOS
        );
        SummaryMetricProvider tenantProvider = provider(
                "tenant-source",
                SummaryMetricScope.TENANT,
                metrics,
                query -> {
                    received.add(query);
                    return List.of(
                            new SummaryMetricValue(
                                    TENANT_ENABLED_FEATURES,
                                    Map.of("items", List.of("knowledge-base", "summary-ops")),
                                    SummaryMetricFreshness.REALTIME,
                                    query.asOf()
                            ),
                            value(TENANT_USER_ROLE_GAPS, query),
                            value(TENANT_KNOWLEDGE_HEALTH, query),
                            value(TENANT_APP_HEALTH, query),
                            value(TENANT_PERMISSION_TODOS, query)
                    );
                }
        );
        SummaryOpsApplicationService service = service(
                List.of(tenantProvider),
                new InMemorySnapshotRepository(),
                Set.of(new PermissionCode(SummaryOpsApplicationService.TENANT_PERMISSION))
        );

        var result = service.tenantWorkbench(TenantId.of("2001"), "1002", "trace-tenant");

        assertThat(received).singleElement().satisfies(query -> {
            assertThat(query.scope()).isEqualTo(SummaryMetricScope.TENANT);
            assertThat(query.tenantId()).isEqualTo("2001");
            assertThat(query.filters()).isEmpty();
        });
        assertThat(result.partial()).isFalse();
        assertThat(result.enabledFeatures()).containsExactly("knowledge-base", "summary-ops");
    }

    private static SummaryOpsApplicationService service(
            List<SummaryMetricProvider> providers,
            SummaryMetricSnapshotRepository snapshots,
            Set<PermissionCode> permissions
    ) {
        RbacAuthorizationQueryPort authorization = new RbacAuthorizationQueryPort() {
            @Override
            public Set<String> findRoleCodes(TenantId tenantId, String actorType, String actorId) {
                return Set.of();
            }

            @Override
            public Set<PermissionCode> findPermissionCodes(TenantId tenantId, String actorType, String actorId) {
                return permissions;
            }
        };
        TenantFeatureGuard featureGuard = new TenantFeatureGuard((tenantId, featureCode) -> true);
        return new SummaryOpsApplicationService(
                providers,
                snapshots,
                authorization,
                featureGuard,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static SummaryMetricProvider provider(
            String source,
            SummaryMetricScope scope,
            Set<String> metrics,
            QueryFunction queryFunction
    ) {
        return new SummaryMetricProvider() {
            @Override
            public String source() {
                return source;
            }

            @Override
            public Set<String> supportedMetricCodes(SummaryMetricScope requestedScope) {
                return requestedScope == scope ? metrics : Set.of();
            }

            @Override
            public List<SummaryMetricValue> query(SummaryMetricQuery query) {
                return queryFunction.query(query);
            }
        };
    }

    private static SummaryMetricValue value(String metricCode, SummaryMetricQuery query) {
        return new SummaryMetricValue(
                metricCode,
                Map.of("count", 1L),
                SummaryMetricFreshness.REALTIME,
                query.asOf()
        );
    }

    @FunctionalInterface
    private interface QueryFunction {
        List<SummaryMetricValue> query(SummaryMetricQuery query);
    }

    private static final class InMemorySnapshotRepository implements SummaryMetricSnapshotRepository {
        private final Map<String, SummaryMetricSnapshot> rows = new LinkedHashMap<>();

        @Override
        public void save(SummaryMetricSnapshot snapshot) {
            rows.put(key(snapshot.tenantId(), snapshot.scope(), snapshot.metricCode()), snapshot);
        }

        @Override
        public List<SummaryMetricSnapshot> findByScope(String tenantId, SummaryMetricScope scope) {
            return rows.values().stream()
                    .filter(row -> row.tenantId().equals(tenantId) && row.scope() == scope)
                    .toList();
        }

        private static String key(String tenantId, SummaryMetricScope scope, String metricCode) {
            return tenantId + ":" + scope + ":" + metricCode;
        }
    }
}
