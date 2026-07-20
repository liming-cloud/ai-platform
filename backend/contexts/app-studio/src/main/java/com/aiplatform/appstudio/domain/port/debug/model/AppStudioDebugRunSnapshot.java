package com.aiplatform.appstudio.domain.port.debug.model;

import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.time.Instant;
import java.util.List;

/**
 * App Studio 调试运行快照，绑定 assistant message 与检索 evidence 历史事实。
 */
public record AppStudioDebugRunSnapshot(
        TenantId tenantId,
        String assistantMessageId,
        String traceId,
        Instant createdAt,
        List<AppStudioDebugRunEvidenceSnapshot> evidences
) implements ValueObject {
    public AppStudioDebugRunSnapshot {
        tenantId = AppStudioPortContracts.requireValue(tenantId, "租户 ID");
        assistantMessageId = AppStudioPortContracts.requireText(assistantMessageId, "助手消息 ID");
        traceId = AppStudioPortContracts.requireText(traceId, "Trace ID");
        createdAt = AppStudioPortContracts.requireValue(createdAt, "创建时间");
        evidences = AppStudioPortContracts.copyRequiredList(evidences, "检索证据快照");
    }
}
