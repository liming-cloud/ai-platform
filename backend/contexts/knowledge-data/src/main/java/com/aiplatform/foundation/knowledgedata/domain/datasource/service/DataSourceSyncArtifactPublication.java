package com.aiplatform.foundation.knowledgedata.domain.datasource.service;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.GovernedDataSource;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.PublishedSyncArtifact;

/** 已由领域服务验证的受治理数据源同步产物发布结果。 */
public interface DataSourceSyncArtifactPublication {
    long expectedSourceVersion();
    GovernedDataSource publishedSource();
    PublishedSyncArtifact publication();
}
