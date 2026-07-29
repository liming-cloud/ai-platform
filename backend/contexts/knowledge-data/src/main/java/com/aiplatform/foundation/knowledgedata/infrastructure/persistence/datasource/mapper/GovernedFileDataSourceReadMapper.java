package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.GovernedFileDataSourceSummaryRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** FILE 来源摘要的受限查询映射器。 */
public interface GovernedFileDataSourceReadMapper {

    List<GovernedFileDataSourceSummaryRow> selectPage(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("offset") long offset,
            @Param("limit") long limit);

    long count(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId);
}
