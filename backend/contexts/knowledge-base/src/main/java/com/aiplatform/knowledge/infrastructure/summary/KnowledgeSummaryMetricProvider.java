package com.aiplatform.knowledge.infrastructure.summary;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.TENANT_KNOWLEDGE_HEALTH;

import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricProvider;
import com.aiplatform.contracts.capability.summary.SummaryMetricQuery;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.contracts.capability.summary.SummaryMetricValue;
import com.aiplatform.knowledge.infrastructure.persistence.mapper.KnowledgeSummaryMetricMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSummaryMetricProvider implements SummaryMetricProvider {
    private final KnowledgeSummaryMetricMapper mapper;

    public KnowledgeSummaryMetricProvider(KnowledgeSummaryMetricMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "知识库汇总指标 Mapper 不能为空");
    }

    @Override
    public String source() {
        return "knowledge-base";
    }

    @Override
    public Set<String> supportedMetricCodes(SummaryMetricScope scope) {
        return scope == SummaryMetricScope.TENANT ? Set.of(TENANT_KNOWLEDGE_HEALTH) : Set.of();
    }

    @Override
    public List<SummaryMetricValue> query(SummaryMetricQuery query) {
        if (query.scope() != SummaryMetricScope.TENANT) {
            return List.of();
        }
        Long tenantId = Long.valueOf(query.tenantId());
        return List.of(new SummaryMetricValue(
                TENANT_KNOWLEDGE_HEALTH,
                Map.of(
                        "total", mapper.countKnowledgeBases(tenantId),
                        "ready", mapper.countReadyKnowledgeBases(tenantId),
                        "unindexed", mapper.countUnindexedKnowledgeBases(tenantId),
                        "failed", mapper.countFailedKnowledgeBases(tenantId)
                ),
                SummaryMetricFreshness.REALTIME,
                query.asOf()
        ));
    }
}
