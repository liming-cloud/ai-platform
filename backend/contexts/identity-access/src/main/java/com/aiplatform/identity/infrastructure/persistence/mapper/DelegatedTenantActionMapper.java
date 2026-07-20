package com.aiplatform.identity.infrastructure.persistence.mapper;

import com.aiplatform.identity.infrastructure.persistence.entity.DelegatedTenantActionPersistenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 平台代管租户动作 Mapper，SQL 统一维护在 XML 中。
 */
@Mapper
public interface DelegatedTenantActionMapper {
    DelegatedTenantActionPersistenceEntity findByRequestHash(
            @Param("tenantId") Long tenantId,
            @Param("operatorId") Long operatorId,
            @Param("requestHash") String requestHash
    );

    Long insertAction(DelegatedTenantActionPersistenceEntity action);
}
