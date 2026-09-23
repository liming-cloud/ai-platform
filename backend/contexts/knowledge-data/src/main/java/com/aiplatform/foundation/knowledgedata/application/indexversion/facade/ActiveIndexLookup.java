package com.aiplatform.foundation.knowledgedata.application.indexversion.facade;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.facade.ActiveIndexSnapshot;
import java.util.Optional;

/**
 * 向其他限界上下文提供知识空间当前活动索引的最小只读事实。
 */
public interface ActiveIndexLookup {

    Optional<ActiveIndexSnapshot> find(TenantId tenantId, long knowledgeSpaceId);
}
