package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.AppPublishPreflightPersistenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppPublishPreflightMapper {
    int insertResult(AppPublishPreflightPersistenceEntity entity);

    AppPublishPreflightPersistenceEntity findByRequestId(
            @Param("tenantId") Long tenantId,
            @Param("applicationId") Long applicationId,
            @Param("publishRequestId") String publishRequestId
    );
}
