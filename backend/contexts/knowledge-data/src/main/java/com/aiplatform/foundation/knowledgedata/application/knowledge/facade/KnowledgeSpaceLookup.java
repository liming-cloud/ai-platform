package com.aiplatform.foundation.knowledgedata.application.knowledge.facade;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * 向其他限界上下文提供租户内知识空间存在性的最小只读事实。
 */
public interface KnowledgeSpaceLookup {

    boolean exists(TenantId tenantId, long knowledgeSpaceId);
}
