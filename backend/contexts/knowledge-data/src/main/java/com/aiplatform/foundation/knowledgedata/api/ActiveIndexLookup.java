package com.aiplatform.foundation.knowledgedata.api;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.api.model.ActiveIndexSnapshot;
import java.util.Optional;
import java.util.UUID;

/**
 * 向其他限界上下文提供知识空间当前活动索引的最小只读事实。
 */
public interface ActiveIndexLookup {

    Optional<ActiveIndexSnapshot> find(TenantId tenantId, UUID knowledgeSpaceId);
}
