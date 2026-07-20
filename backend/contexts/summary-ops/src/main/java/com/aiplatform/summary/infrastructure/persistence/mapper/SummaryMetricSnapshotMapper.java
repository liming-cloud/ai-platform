package com.aiplatform.summary.infrastructure.persistence.mapper;

import com.aiplatform.summary.infrastructure.persistence.entity.SummaryMetricSnapshotPersistenceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SummaryMetricSnapshotMapper {
    int upsert(SummaryMetricSnapshotPersistenceEntity entity);

    List<SummaryMetricSnapshotPersistenceEntity> findByScope(
            @Param("tenantId") Long tenantId,
            @Param("scopeType") String scopeType
    );
}
