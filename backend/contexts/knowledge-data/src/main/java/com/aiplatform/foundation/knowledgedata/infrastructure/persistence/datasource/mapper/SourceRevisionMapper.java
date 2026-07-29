package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.SourceRevisionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 来源定义修订的数据库映射器。
 */
public interface SourceRevisionMapper {

    @Insert("""
            INSERT INTO knowledge_data.source_revision (
                tenant_id, knowledge_space_id, data_source_id, revision,
                source_type, definition_digest, created_by, created_at
            ) VALUES (
                #{entity.tenantId}, #{entity.knowledgeSpaceId}, #{entity.dataSourceId}, #{entity.revision},
                #{entity.sourceType}, #{entity.definitionDigest}, #{entity.createdBy}, #{entity.createdAt}
            )
            """)
    int insert(@Param("entity") SourceRevisionEntity entity);

    @Select("""
            SELECT tenant_id, knowledge_space_id, data_source_id, revision,
                   source_type, definition_digest, created_by, created_at
            FROM knowledge_data.source_revision
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND data_source_id = #{dataSourceId}
              AND revision = #{revision}
            """)
    SourceRevisionEntity findByScope(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("revision") long revision);

    @Select("""
            SELECT count(*)
            FROM knowledge_data.source_revision
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND data_source_id = #{dataSourceId}
              AND revision = #{revision}
            """)
    long countByScope(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("dataSourceId") long dataSourceId,
            @Param("revision") long revision);
}
