package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelSummaryMetricMapper {
    long countModelConfigs();

    long countHealthyModelConfigs();

    long countFailedModelConfigs();

    long countPendingApprovalResources();
}
