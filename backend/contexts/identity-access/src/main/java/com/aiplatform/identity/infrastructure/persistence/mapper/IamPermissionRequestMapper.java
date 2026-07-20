package com.aiplatform.identity.infrastructure.persistence.mapper;

import com.aiplatform.identity.infrastructure.persistence.entity.IamPermissionRequestPersistenceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IamPermissionRequestMapper {
    int insertRequest(IamPermissionRequestPersistenceEntity entity);
    int updateDecision(IamPermissionRequestPersistenceEntity entity);
    IamPermissionRequestPersistenceEntity findById(@Param("tenantId") Long tenantId, @Param("requestId") String requestId);
    long countByTenant(
            @Param("tenantId") Long tenantId,
            @Param("status") String status
    );
    List<IamPermissionRequestPersistenceEntity> findPage(
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}
