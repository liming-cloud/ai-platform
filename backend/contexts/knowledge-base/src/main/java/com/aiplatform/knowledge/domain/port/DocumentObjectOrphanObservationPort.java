package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.StoredDocumentObject;
import com.aiplatform.shared.kernel.TenantId;

/**
 * object-first 上传在数据库提交失败后的孤儿对象观测 seam。
 *
 * <p>维护任务可根据这些结构化事实执行延迟清理；业务请求不得在失败路径同步删除未知对象。</p>
 */
public interface DocumentObjectOrphanObservationPort {
    void observePotentialOrphan(
            TenantId tenantId,
            String knowledgeBaseId,
            String documentId,
            StoredDocumentObject storedObject,
            String failureType
    );
}
