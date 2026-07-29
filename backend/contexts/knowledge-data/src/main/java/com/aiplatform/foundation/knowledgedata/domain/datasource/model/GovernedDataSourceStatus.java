package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

/**
 * 受治理数据源的可恢复生命周期状态。
 */
public enum GovernedDataSourceStatus {
    DRAFT,
    PROBING,
    READY,
    SYNCING,
    FAILED,
    RETIRED
}
