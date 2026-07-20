package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationReleasePersistenceEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppApplicationReleaseMapper {
    Instant lockApplication(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId
    );

    Long nextReleaseId();

    Long nextVersion(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId
    );

    int insertRelease(AppApplicationReleasePersistenceEntity entity);

    int activateRelease(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("releaseId") long releaseId,
            @Param("actorId") long actorId,
            @Param("now") Instant now
    );

    int markApplicationPublished(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("actorId") long actorId,
            @Param("now") Instant now
    );

    AppApplicationReleasePersistenceEntity findActive(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId
    );

    List<AppApplicationReleasePersistenceEntity> findActiveByApplicationIds(
            @Param("tenantId") long tenantId,
            @Param("applicationIds") List<Long> applicationIds
    );

    AppApplicationReleasePersistenceEntity findByRequestId(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("requestId") String requestId
    );

    AppApplicationReleasePersistenceEntity findById(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId,
            @Param("releaseId") long releaseId
    );

    List<AppApplicationReleasePersistenceEntity> findAll(
            @Param("tenantId") long tenantId,
            @Param("applicationId") long applicationId
    );
}
