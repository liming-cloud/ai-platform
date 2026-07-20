package com.aiplatform.identity.infrastructure.summary;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_TENANT_HEALTH;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_ENABLED_FEATURES;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_USER_ROLE_GAPS;

import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricProvider;
import com.aiplatform.contracts.capability.summary.SummaryMetricQuery;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.contracts.capability.summary.SummaryMetricValue;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamSummaryMetricMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class IdentitySummaryMetricProvider implements SummaryMetricProvider {
    private final IamSummaryMetricMapper mapper;

    public IdentitySummaryMetricProvider(IamSummaryMetricMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "IAM 汇总指标 Mapper 不能为空");
    }

    @Override
    public String source() {
        return "identity-access";
    }

    @Override
    public Set<String> supportedMetricCodes(SummaryMetricScope scope) {
        return scope == SummaryMetricScope.PLATFORM
                ? Set.of(PLATFORM_TENANT_HEALTH)
                : Set.of(TENANT_ENABLED_FEATURES, TENANT_USER_ROLE_GAPS);
    }

    @Override
    public List<SummaryMetricValue> query(SummaryMetricQuery query) {
        if (query.scope() == SummaryMetricScope.PLATFORM) {
            String tenantStatus = query.filters().get("tenantStatus");
            return List.of(new SummaryMetricValue(
                    PLATFORM_TENANT_HEALTH,
                    Map.of(
                            "total", mapper.countTenants(tenantStatus),
                            "active", mapper.countActiveTenants(tenantStatus),
                            "abnormal", mapper.countAbnormalTenants(tenantStatus),
                            "missingTenantAdministrator", mapper.countTenantsMissingAdministrator(tenantStatus)
                    ),
                    SummaryMetricFreshness.REALTIME,
                    query.asOf()
            ));
        }
        Long tenantId = Long.valueOf(query.tenantId());
        List<String> enabledFeatures = Objects.requireNonNull(
                mapper.findEnabledFeatureCodes(tenantId),
                "租户功能指标结果不能为空"
        );
        return List.of(
                new SummaryMetricValue(
                        TENANT_ENABLED_FEATURES,
                        Map.of("items", List.copyOf(enabledFeatures)),
                        SummaryMetricFreshness.REALTIME,
                        query.asOf()
                ),
                new SummaryMetricValue(
                        TENANT_USER_ROLE_GAPS,
                        Map.of(
                                "activeUsers", mapper.countActiveUsers(tenantId),
                                "usersWithoutRole", mapper.countActiveUsersWithoutRole(tenantId),
                                "rolesWithoutPermission", mapper.countActiveRolesWithoutPermission(tenantId)
                        ),
                        SummaryMetricFreshness.REALTIME,
                        query.asOf()
                )
        );
    }
}
