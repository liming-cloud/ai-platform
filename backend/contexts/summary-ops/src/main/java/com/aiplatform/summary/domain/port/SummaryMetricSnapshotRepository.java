package com.aiplatform.summary.domain.port;

import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.summary.domain.SummaryMetricSnapshot;
import java.util.List;

public interface SummaryMetricSnapshotRepository {
    void save(SummaryMetricSnapshot snapshot);

    List<SummaryMetricSnapshot> findByScope(String tenantId, SummaryMetricScope scope);
}
