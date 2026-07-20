package com.aiplatform.audit.infrastructure.summary;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_RAG_SECURITY;

import com.aiplatform.audit.infrastructure.persistence.mapper.AuditSummaryMetricMapper;
import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricProvider;
import com.aiplatform.contracts.capability.summary.SummaryMetricQuery;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.contracts.capability.summary.SummaryMetricValue;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AuditSummaryMetricProvider implements SummaryMetricProvider {
    private final AuditSummaryMetricMapper mapper;

    public AuditSummaryMetricProvider(AuditSummaryMetricMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "审计汇总指标 Mapper 不能为空");
    }

    @Override
    public String source() {
        return "observability-audit";
    }

    @Override
    public Set<String> supportedMetricCodes(SummaryMetricScope scope) {
        return scope == SummaryMetricScope.PLATFORM ? Set.of(PLATFORM_RAG_SECURITY) : Set.of();
    }

    @Override
    public List<SummaryMetricValue> query(SummaryMetricQuery query) {
        if (query.scope() != SummaryMetricScope.PLATFORM) {
            return List.of();
        }
        return List.of(new SummaryMetricValue(
                PLATFORM_RAG_SECURITY,
                Map.of(
                        "authorizationDenialsLast24Hours",
                        mapper.countRagAuthorizationDenialsSince(query.asOf().minus(24, ChronoUnit.HOURS))
                ),
                SummaryMetricFreshness.REALTIME,
                query.asOf()
        ));
    }
}
