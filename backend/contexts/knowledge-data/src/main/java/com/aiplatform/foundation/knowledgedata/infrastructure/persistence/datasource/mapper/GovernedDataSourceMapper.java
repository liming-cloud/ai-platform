package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity.GovernedDataSourceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * 受治理数据源身份的数据库映射器。
 */
public interface GovernedDataSourceMapper extends BaseMapper<GovernedDataSourceEntity> {

    GovernedDataSourceEntity findByScopeForUpdate(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id);

    int updateLifecycle(
            @Param("tenantId") long tenantId,
            @Param("knowledgeSpaceId") long knowledgeSpaceId,
            @Param("id") long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("status") String status,
            @Param("failedOperation") String failedOperation);
}
