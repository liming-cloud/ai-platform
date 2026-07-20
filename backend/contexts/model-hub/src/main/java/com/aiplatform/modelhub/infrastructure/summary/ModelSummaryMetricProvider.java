package com.aiplatform.modelhub.infrastructure.summary;

import static com.aiplatform.contracts.capability.summary.SummaryMetricCodes.PLATFORM_MODEL_HEALTH;

import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricProvider;
import com.aiplatform.contracts.capability.summary.SummaryMetricQuery;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.contracts.capability.summary.SummaryMetricValue;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelSummaryMetricMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ModelSummaryMetricProvider implements SummaryMetricProvider {
    private final ModelSummaryMetricMapper mapper;

    public ModelSummaryMetricProvider(ModelSummaryMetricMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型汇总指标 Mapper 不能为空");
    }

    @Override
    public String source() {
        return "model-hub";
    }

    @Override
    public Set<String> supportedMetricCodes(SummaryMetricScope scope) {
        return scope == SummaryMetricScope.PLATFORM ? Set.of(PLATFORM_MODEL_HEALTH) : Set.of();
    }

    @Override
    public List<SummaryMetricValue> query(SummaryMetricQuery query) {
        if (query.scope() != SummaryMetricScope.PLATFORM) {
            return List.of();
        }
        return List.of(new SummaryMetricValue(
                PLATFORM_MODEL_HEALTH,
                Map.of(
                        "total", mapper.countModelConfigs(),
                        "healthy", mapper.countHealthyModelConfigs(),
                        "failed", mapper.countFailedModelConfigs(),
                        "pendingApproval", mapper.countPendingApprovalResources()
                ),
                SummaryMetricFreshness.REALTIME,
                query.asOf()
        ));
    }
}
