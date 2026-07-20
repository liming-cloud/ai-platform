package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppSummaryMetricMapper {
    long countPlatformPublishBlockedApplications();

    long countApplications(@Param("tenantId") Long tenantId);

    long countPublishedApplications(@Param("tenantId") Long tenantId);

    long countDraftApplications(@Param("tenantId") Long tenantId);

    long countPublishBlockedApplications(@Param("tenantId") Long tenantId);
}
