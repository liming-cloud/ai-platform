package com.aiplatform.approval.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApprovalSummaryMetricMapper {
    long countPlatformPendingTasks();

    long countTenantPermissionPendingTasks(@Param("tenantId") Long tenantId);
}
