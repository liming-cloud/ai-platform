package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.ObjectStorageObjectEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 对象存储来源修订冻结对象的数据库映射器。
 */
public interface ObjectStorageObjectMapper {

    int insert(@Param("entity") ObjectStorageObjectEntity entity);

    List<ObjectStorageObjectEntity> findByScope(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("revision") long revision);
}
