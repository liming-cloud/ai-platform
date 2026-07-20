package com.aiplatform.appstudio.infrastructure.summary;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_APP_PUBLISH_BLOCKS;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_APP_HEALTH;

import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppSummaryMetricMapper;
import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricProvider;
import com.aiplatform.contracts.capability.summary.SummaryMetricQuery;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.contracts.capability.summary.SummaryMetricValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AppSummaryMetricProvider implements SummaryMetricProvider {
    private final AppSummaryMetricMapper mapper;

    public AppSummaryMetricProvider(AppSummaryMetricMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "应用汇总指标 Mapper 不能为空");
    }

    @Override
    public String source() {
        return "app-studio";
    }

    @Override
    public Set<String> supportedMetricCodes(SummaryMetricScope scope) {
        return scope == SummaryMetricScope.PLATFORM
                ? Set.of(PLATFORM_APP_PUBLISH_BLOCKS)
                : Set.of(TENANT_APP_HEALTH);
    }

    @Override
    public List<SummaryMetricValue> query(SummaryMetricQuery query) {
        if (query.scope() == SummaryMetricScope.PLATFORM) {
            return List.of(new SummaryMetricValue(
                    PLATFORM_APP_PUBLISH_BLOCKS,
                    Map.of("blocked", mapper.countPlatformPublishBlockedApplications()),
                    SummaryMetricFreshness.REALTIME,
                    query.asOf()
            ));
        }
        Long tenantId = Long.valueOf(query.tenantId());
        return List.of(new SummaryMetricValue(
                TENANT_APP_HEALTH,
                Map.of(
                        "total", mapper.countApplications(tenantId),
                        "published", mapper.countPublishedApplications(tenantId),
                        "draft", mapper.countDraftApplications(tenantId),
                        "publishBlocked", mapper.countPublishBlockedApplications(tenantId)
                ),
                SummaryMetricFreshness.REALTIME,
                query.asOf()
        ));
    }
}
