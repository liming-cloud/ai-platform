package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.GovernedDataSourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 受治理数据源身份的数据库映射器。
 */
public interface GovernedDataSourceMapper extends BaseMapper<GovernedDataSourceEntity> {

    @Select("""
            SELECT id, tenant_id, knowledge_space_id, source_type, name, status,
                   failed_operation, version, created_by, created_at
            FROM knowledge_data.governed_data_source
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
            FOR UPDATE
            """)
    GovernedDataSourceEntity findByScopeForUpdate(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    @Update("""
            UPDATE knowledge_data.governed_data_source
            SET status = #{status}, failed_operation = #{failedOperation}, version = #{nextVersion}
            WHERE tenant_id = #{tenantId}
              AND knowledge_space_id = #{knowledgeSpaceId}
              AND id = #{id}
              AND version = #{expectedVersion}
            """)
    int updateLifecycle(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("status") String status,
            @Param("failedOperation") String failedOperation);
}
