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
import com.aiplatform.summary.application.result.PlatformOverviewResult;
import com.aiplatform.summary.application.result.TenantWorkbenchResult;
import com.aiplatform.summary.domain.SummaryMetricSnapshot;
import com.aiplatform.summary.domain.SummaryOpsErrorCodes;
import com.aiplatform.summary.domain.port.SummaryMetricSnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SummaryOpsApplicationService {
    public static final String PLATFORM_PERMISSION = "summary-ops:platform-overview:read";
    public static final String TENANT_PERMISSION = "summary-ops:tenant-workbench:read";
    public static final String FEATURE_CODE = "summary-ops";

    private static final List<String> PLATFORM_METRICS = List.of(
            PLATFORM_TENANT_HEALTH,
            PLATFORM_MODEL_HEALTH,
            PLATFORM_APPROVAL_TODOS,
            PLATFORM_RAG_SECURITY,
            PLATFORM_APP_PUBLISH_BLOCKS
    );
    private static final List<String> TENANT_METRICS = List.of(
            TENANT_ENABLED_FEATURES,
            TENANT_USER_ROLE_GAPS,
            TENANT_KNOWLEDGE_HEALTH,
            TENANT_APP_HEALTH,
            TENANT_PERMISSION_TODOS
    );
    private static final Map<String, String> PLATFORM_SCOPE_ALIASES = Map.ofEntries(
            Map.entry("tenantHealth", PLATFORM_TENANT_HEALTH),
            Map.entry("modelHealth", PLATFORM_MODEL_HEALTH),
            Map.entry("approvalTodos", PLATFORM_APPROVAL_TODOS),
            Map.entry("ragSecurity", PLATFORM_RAG_SECURITY),
            Map.entry("appPublishBlocks", PLATFORM_APP_PUBLISH_BLOCKS)
    );

    private final List<SummaryMetricProvider> providers;
    private final SummaryMetricSnapshotRepository snapshots;
    private final RbacAuthorizationQueryPort authorizationQuery;
    private final TenantFeatureGuard featureGuard;
    private final Clock clock;

    public SummaryOpsApplicationService(
            List<SummaryMetricProvider> providers,
            SummaryMetricSnapshotRepository snapshots,
            RbacAuthorizationQueryPort authorizationQuery,
            TenantFeatureGuard featureGuard,
            Clock clock
    ) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "指标提供者不能为空"));
        this.snapshots = Objects.requireNonNull(snapshots, "指标快照仓储不能为空");
        this.authorizationQuery = Objects.requireNonNull(authorizationQuery, "RBAC 查询端口不能为空");
        this.featureGuard = Objects.requireNonNull(featureGuard, "租户功能守卫不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public PlatformOverviewResult platformOverview(
            TenantId operatorTenantId,
            String actorId,
            String tenantStatus,
            String metricScope,
            String traceId
    ) {
        requirePermission(operatorTenantId, actorId, PLATFORM_PERMISSION);
        List<String> selectedMetrics = selectPlatformMetrics(metricScope);
        Map<String, String> filters = normalizeOptional(tenantStatus) == null
                ? Map.of()
                : Map.of("tenantStatus", tenantStatus.trim().toUpperCase(Locale.ROOT));
        SummaryMetricAggregation aggregation = aggregate(
                SummaryMetricScope.PLATFORM,
                "0",
                selectedMetrics,
                filters,
                traceId
        );
        return new PlatformOverviewResult(
                aggregation.asOf(),
                aggregation.freshness(),
                aggregation.partial(),
                aggregation.failedMetrics(),
                aggregation.traceId(),
                aggregation.payload(PLATFORM_TENANT_HEALTH),
                aggregation.payload(PLATFORM_MODEL_HEALTH),
                aggregation.payload(PLATFORM_APPROVAL_TODOS),
                aggregation.payload(PLATFORM_RAG_SECURITY),
                aggregation.payload(PLATFORM_APP_PUBLISH_BLOCKS)
        );
    }

    public TenantWorkbenchResult tenantWorkbench(TenantId tenantId, String actorId, String traceId) {
        requirePermission(tenantId, actorId, TENANT_PERMISSION);
        featureGuard.requireEnabled(tenantId, FEATURE_CODE);
        SummaryMetricAggregation aggregation = aggregate(
                SummaryMetricScope.TENANT,
                tenantId.value(),
                TENANT_METRICS,
                Map.of(),
                traceId
        );
        return new TenantWorkbenchResult(
                tenantId.value(),
                aggregation.asOf(),
                aggregation.freshness(),
                aggregation.partial(),
                aggregation.failedMetrics(),
                aggregation.traceId(),
                stringList(aggregation.payload(TENANT_ENABLED_FEATURES).get("items")),
                aggregation.payload(TENANT_USER_ROLE_GAPS),
                aggregation.payload(TENANT_KNOWLEDGE_HEALTH),
                aggregation.payload(TENANT_APP_HEALTH),
                aggregation.payload(TENANT_PERMISSION_TODOS)
        );
    }

    private SummaryMetricAggregation aggregate(
            SummaryMetricScope scope,
            String tenantId,
            List<String> expectedMetrics,
            Map<String, String> filters,
            String traceId
    ) {
        Instant requestedAt = Instant.now(clock);
        Set<String> expected = new LinkedHashSet<>(expectedMetrics);
        Map<String, SummaryMetricSnapshot> values = new LinkedHashMap<>();
        Set<String> failed = new LinkedHashSet<>();
        SummaryMetricQuery query = new SummaryMetricQuery(scope, tenantId, requestedAt, filters);

        providers.forEach(provider -> collectProvider(provider, query, expected, values, failed));

        expected.stream().filter(metric -> !values.containsKey(metric)).forEach(failed::add);
        if (!failed.isEmpty()) {
            loadFallbackSnapshots(tenantId, scope, failed, values);
            markFailedSnapshots(failed, tenantId, scope, values);
        }
        return new SummaryMetricAggregation(
                requestedAt,
                responseFreshness(requestedAt, values, failed),
                !failed.isEmpty(),
                expectedMetrics.stream().filter(failed::contains).toList(),
                requireText(traceId, "traceId"),
                Map.copyOf(values)
        );
    }

    private void collectProvider(
            SummaryMetricProvider provider,
            SummaryMetricQuery query,
            Set<String> expected,
            Map<String, SummaryMetricSnapshot> values,
            Set<String> failed
    ) {
        Set<String> supported = new LinkedHashSet<>();
        try {
            supported.addAll(requiredCodes(provider.supportedMetricCodes(query.scope())).stream()
                    .filter(expected::contains)
                    .toList());
            if (supported.isEmpty()) {
                return;
            }
            List<SummaryMetricValue> queried = Objects.requireNonNull(provider.query(query),
                    provider.source() + " 指标查询结果不能为空");
            for (SummaryMetricValue value : queried) {
                if (!supported.contains(value.metricCode())) {
                    throw new IllegalStateException(provider.source() + " 返回未声明指标 " + value.metricCode());
                }
                SummaryMetricSnapshot snapshot = new SummaryMetricSnapshot(
                        query.tenantId(),
                        query.scope(),
                        value.metricCode(),
                        value.payload(),
                        value.freshness(),
                        false,
                        null,
                        value.asOf()
                );
                snapshots.save(snapshot);
                if (values.putIfAbsent(value.metricCode(), snapshot) != null) {
                    throw new IllegalStateException("指标重复返回：" + value.metricCode());
                }
            }
            supported.stream().filter(metric -> !values.containsKey(metric)).forEach(failed::add);
        } catch (RuntimeException exception) {
            failed.addAll(supported);
        }
    }

    private void loadFallbackSnapshots(
            String tenantId,
            SummaryMetricScope scope,
            Set<String> failed,
            Map<String, SummaryMetricSnapshot> values
    ) {
        try {
            for (SummaryMetricSnapshot snapshot : snapshots.findByScope(tenantId, scope)) {
                if (failed.contains(snapshot.metricCode())) {
                    values.putIfAbsent(snapshot.metricCode(), snapshot);
                }
            }
        } catch (RuntimeException ignored) {
            // Snapshot storage is an availability aid; source failures must still return a partial response.
        }
    }

    private void markFailedSnapshots(
            Set<String> failed,
            String tenantId,
            SummaryMetricScope scope,
            Map<String, SummaryMetricSnapshot> values
    ) {
        for (String metric : failed) {
            SummaryMetricSnapshot current = values.getOrDefault(metric, new SummaryMetricSnapshot(
                    tenantId,
                    scope,
                    metric,
                    Map.of(),
                    SummaryMetricFreshness.REALTIME,
                    true,
                    "metric_source_unavailable",
                    Instant.now(clock)
            ));
            try {
                snapshots.save(current.failed("metric_source_unavailable"));
            } catch (RuntimeException ignored) {
                // The response remains partial even when the failure marker itself cannot be persisted.
            }
        }
    }

    private void requirePermission(TenantId tenantId, String actorId, String permission) {
        if (!authorizationQuery.findPermissionCodes(
                Objects.requireNonNull(tenantId, "租户 ID 不能为空"),
                "user",
                requireText(actorId, "操作者 ID")
        ).contains(new PermissionCode(permission))) {
            throw new ApplicationException(SummaryOpsErrorCodes.AUTHORIZATION_DENIED, "当前主体无权读取该汇总视图");
        }
    }

    private static List<String> selectPlatformMetrics(String metricScope) {
        String normalized = normalizeOptional(metricScope);
        if (normalized == null) {
            return PLATFORM_METRICS;
        }
        String metric = PLATFORM_SCOPE_ALIASES.getOrDefault(normalized, normalized);
        if (!PLATFORM_METRICS.contains(metric)) {
            throw new IllegalArgumentException("不支持的 metricScope：" + normalized);
        }
        return List.of(metric);
    }

    private static String responseFreshness(
            Instant requestedAt,
            Map<String, SummaryMetricSnapshot> values,
            Set<String> failed
    ) {
        if (failed.isEmpty()) {
            return SummaryMetricFreshness.REALTIME.name();
        }
        Instant oldest = values.values().stream()
                .map(SummaryMetricSnapshot::asOf)
                .min(Comparator.naturalOrder())
                .orElse(requestedAt);
        Duration age = Duration.between(oldest, requestedAt);
        if (age.compareTo(Duration.ofMinutes(1)) <= 0) {
            return SummaryMetricFreshness.ONE_MINUTE.name();
        }
        if (age.compareTo(Duration.ofMinutes(5)) <= 0) {
            return SummaryMetricFreshness.FIVE_MINUTES.name();
        }
        return "STALE";
    }

    private static Set<String> requiredCodes(Set<String> codes) {
        return Objects.requireNonNull(codes, "提供者指标编码不能为空");
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object item : values) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
