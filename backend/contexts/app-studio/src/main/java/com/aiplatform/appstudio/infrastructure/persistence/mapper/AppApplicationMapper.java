package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import com.aiplatform.appstudio.infrastructure.persistence.entity.AppApplicationPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.entity.AppBoundKnowledgeBasePersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppApplicationMapper extends BaseMapper<AppApplicationPersistenceEntity> {
    Long nextApplicationId();

    Instant lockApplication(@Param("tenantId") Long tenantId, @Param("applicationId") Long applicationId);

    List<AppBoundKnowledgeBasePersistenceEntity> findActiveBindings(@Param("tenantId") Long tenantId, @Param("applicationId") Long applicationId);

    List<AppBoundKnowledgeBasePersistenceEntity> findActiveBindingsByApplicationIds(
            @Param("tenantId") Long tenantId,
            @Param("applicationIds") List<Long> applicationIds
    );

    int softDeleteBindings(@Param("tenantId") Long tenantId, @Param("applicationId") Long applicationId, @Param("updatedBy") Long updatedBy);

    int insertBinding(AppBoundKnowledgeBasePersistenceEntity entity);
}
