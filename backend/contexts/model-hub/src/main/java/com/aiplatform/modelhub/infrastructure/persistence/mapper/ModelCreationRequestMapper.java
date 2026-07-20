package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.modelhub.infrastructure.persistence.entity.ModelCreationRequestPersistenceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL mapper for atomic ModelHub creation request claims. */
@Mapper
public interface ModelCreationRequestMapper {
    Long insertClaim(ModelCreationRequestPersistenceEntity request);

    ModelCreationRequestPersistenceEntity findClaim(
            @Param("tenantId") Long tenantId,
            @Param("resourceType") String resourceType,
            @Param("requestId") String requestId
    );

    int completeClaim(ModelCreationRequestPersistenceEntity request);
}
