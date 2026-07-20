package com.aiplatform.approval.infrastructure.summary;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_APPROVAL_TODOS;
import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_PERMISSION_TODOS;

import com.aiplatform.approval.infrastructure.persistence.mapper.ApprovalSummaryMetricMapper;
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
public class ApprovalSummaryMetricProvider implements SummaryMetricProvider {
    private final ApprovalSummaryMetricMapper mapper;

    public ApprovalSummaryMetricProvider(ApprovalSummaryMetricMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "审批汇总指标 Mapper 不能为空");
    }

    @Override
    public String source() {
        return "approval-center";
    }

    @Override
    public Set<String> supportedMetricCodes(SummaryMetricScope scope) {
        return scope == SummaryMetricScope.PLATFORM
                ? Set.of(PLATFORM_APPROVAL_TODOS)
                : Set.of(TENANT_PERMISSION_TODOS);
    }

    @Override
    public List<SummaryMetricValue> query(SummaryMetricQuery query) {
        if (query.scope() == SummaryMetricScope.PLATFORM) {
            return List.of(new SummaryMetricValue(
                    PLATFORM_APPROVAL_TODOS,
                    Map.of("pending", mapper.countPlatformPendingTasks()),
                    SummaryMetricFreshness.REALTIME,
                    query.asOf()
            ));
        }
        return List.of(new SummaryMetricValue(
                TENANT_PERMISSION_TODOS,
                Map.of("pending", mapper.countTenantPermissionPendingTasks(Long.valueOf(query.tenantId()))),
                SummaryMetricFreshness.REALTIME,
                query.asOf()
        ));
    }
}
