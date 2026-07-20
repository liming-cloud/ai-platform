package com.aiplatform.identity.infrastructure.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IamSummaryMetricMapper {
    long countTenants(@Param("tenantStatus") String tenantStatus);

    long countActiveTenants(@Param("tenantStatus") String tenantStatus);

    long countAbnormalTenants(@Param("tenantStatus") String tenantStatus);

    long countTenantsMissingAdministrator(@Param("tenantStatus") String tenantStatus);

    List<String> findEnabledFeatureCodes(@Param("tenantId") Long tenantId);

    long countActiveUsers(@Param("tenantId") Long tenantId);

    long countActiveUsersWithoutRole(@Param("tenantId") Long tenantId);

    long countActiveRolesWithoutPermission(@Param("tenantId") Long tenantId);
}
