package com.aiplatform.contracts.capability.summary;

import java.util.List;
import java.util.Set;

/**
 * Open query contract implemented by the bounded context that owns each metric source.
 */
public interface SummaryMetricProvider {
    String source();

    Set<String> supportedMetricCodes(SummaryMetricScope scope);

    List<SummaryMetricValue> query(SummaryMetricQuery query);
}
