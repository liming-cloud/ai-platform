package com.aiplatform.foundation.knowledgedata.application.datasource.dto;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSourceStatus;

/**
 * 生命周期动作完成后的最小来源状态投影。
 */
public record GovernedDataSourceLifecycleResult(
        String dataSourceId,
        String knowledgeSpaceId,
        GovernedDataSourceStatus status,
        long version) { }
