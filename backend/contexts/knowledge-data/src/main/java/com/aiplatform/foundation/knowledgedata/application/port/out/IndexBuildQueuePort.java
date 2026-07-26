package com.aiplatform.foundation.knowledgedata.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 支持并发安全认领与稳定失败登记的索引构建队列。
 */
public interface IndexBuildQueuePort {

    Optional<IndexBuildLease> claim(Instant now, Duration leaseDuration);

    void recordFailure(
            IndexBuildLease value,
            String code,
            Instant failedAt);
}
