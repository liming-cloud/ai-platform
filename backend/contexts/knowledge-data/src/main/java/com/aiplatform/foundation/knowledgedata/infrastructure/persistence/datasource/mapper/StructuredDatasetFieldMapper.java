package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.StructuredDatasetFieldEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 结构化数据集修订冻结字段映射的数据库映射器。 */
public interface StructuredDatasetFieldMapper {

    int insert(@Param("entity") StructuredDatasetFieldEntity entity);

    List<StructuredDatasetFieldEntity> findByScope(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("revision") long revision);
}
