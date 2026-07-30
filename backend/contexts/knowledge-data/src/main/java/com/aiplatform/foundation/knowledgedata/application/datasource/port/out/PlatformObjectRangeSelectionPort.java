package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.ObjectStorageObjectCollection;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectRangeId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 解析部署侧预置且已授权的平台对象范围选择。 */
public interface PlatformObjectRangeSelectionPort {

    /**
     * 返回与请求范围及选择修订完全一致的冻结对象集合。
     *
     * @throws IllegalArgumentException 范围不属于该租户或知识空间、选择修订不存在或已失效时抛出。
     */
    ObjectStorageObjectCollection resolve(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            PlatformObjectRangeId rangeId,
            long selectionRevision);
}
