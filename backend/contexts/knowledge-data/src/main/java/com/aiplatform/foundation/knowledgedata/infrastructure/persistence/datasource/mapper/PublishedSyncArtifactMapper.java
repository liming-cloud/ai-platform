package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.PublishedSyncArtifactEntity;
import org.apache.ibatis.annotations.Param;

/** 已发布同步产物数据库映射器。 */
public interface PublishedSyncArtifactMapper {
    int insert(PublishedSyncArtifactEntity value);
    PublishedSyncArtifactEntity findByArtifact(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("artifactId") long artifactId);
}
