package com.aiplatform.knowledge.domain.port.maintenance;

import java.time.Instant;

/** 清理既非当前 active，也不属于任何 ACTIVE rebuild cohort 的过期候选向量。 */
public interface KnowledgeIndexMaintenancePort {
    int deleteObsoleteEmbeddings(Instant updatedBefore, int batchSize);
}
