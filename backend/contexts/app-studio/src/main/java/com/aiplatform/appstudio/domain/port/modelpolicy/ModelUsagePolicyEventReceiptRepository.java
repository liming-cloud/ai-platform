package com.aiplatform.appstudio.domain.port.modelpolicy;

import java.time.Instant;

/**
 * app-studio 模型策略事件持久化消费凭据，以 eventId 提供跨进程幂等。
 */
public interface ModelUsagePolicyEventReceiptRepository {
    boolean recordIfAbsent(String eventId, long eventVersion, Instant occurredAt);
}
