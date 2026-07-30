package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.StructuredDatasetCsvDefinition;

/** 按冻结私有 CSV 与字段映射生成可索引文本投影的技术端口。 */
public interface StructuredDatasetCsvProjectionPort {
    StructuredDatasetCsvProjection project(StructuredDatasetCsvDefinition definition);
}
