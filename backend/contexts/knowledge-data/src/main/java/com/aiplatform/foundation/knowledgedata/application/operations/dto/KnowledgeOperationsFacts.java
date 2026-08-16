package com.aiplatform.foundation.knowledgedata.application.operations.dto;

import java.util.Map;

/** 知识数据上下文拥有的脱敏资源状态计数。 */
public record KnowledgeOperationsFacts(
        Map<String, Long> spacesByStatus,
        Map<String, Long> dataSourcesByStatus,
        Map<String, Long> indexesByStatus,
        Map<String, Long> indexJobsByStatus) {

    public KnowledgeOperationsFacts {
        spacesByStatus = Map.copyOf(spacesByStatus);
        dataSourcesByStatus = Map.copyOf(dataSourcesByStatus);
        indexesByStatus = Map.copyOf(indexesByStatus);
        indexJobsByStatus = Map.copyOf(indexJobsByStatus);
    }
}
