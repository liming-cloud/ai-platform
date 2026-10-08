package com.aiplatform.foundation.knowledgedata.application.knowledge.facade;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 只返回受信任租户内知识库名称，供助手派生范围展示，不读取正文或允许另选知识库。 */
@FunctionalInterface
public interface KnowledgeSpaceNameLookup {
    /** 非删除知识库的当前名称；对象不可见或缺失返回空。 */
    Optional<String> findName(TenantId tenantId, long knowledgeSpaceId);
}
