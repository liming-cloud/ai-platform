package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.SourceRevisionEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 来源定义修订的数据库映射器。
 */
public interface SourceRevisionMapper {

    int insert(@Param("entity") SourceRevisionEntity entity);

    SourceRevisionEntity findByScope(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("revision") long revision);

    long countByScope(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("revision") long revision);
}
