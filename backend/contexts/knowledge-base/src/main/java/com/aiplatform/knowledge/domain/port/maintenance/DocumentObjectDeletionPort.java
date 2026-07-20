package com.aiplatform.knowledge.domain.port.maintenance;

import com.aiplatform.knowledge.domain.maintenance.ClaimedDocumentObjectCleanupTask;

/** 幂等删除经租户、知识库和文档身份验证的孤儿对象。 */
public interface DocumentObjectDeletionPort {
    void delete(ClaimedDocumentObjectCleanupTask claimed);
}
