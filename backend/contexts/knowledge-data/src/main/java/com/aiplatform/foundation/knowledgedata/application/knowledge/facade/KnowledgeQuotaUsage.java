package com.aiplatform.foundation.knowledgedata.application.knowledge.facade;

/** 向平台配额治理提供不含知识正文及来源标识的已用量投影。 */
public interface KnowledgeQuotaUsage {

    /** 读取指定租户的用量；无法核实的来源必须在结果中标记为不完整。 */
    KnowledgeQuotaUsageSnapshot snapshot(long tenantId);
}
