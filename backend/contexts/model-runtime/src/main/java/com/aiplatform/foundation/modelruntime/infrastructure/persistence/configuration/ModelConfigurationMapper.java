package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity.ModelConfigurationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 受控目录与模型配置的持久化映射器。
 */
public interface ModelConfigurationMapper extends BaseMapper<ModelConfigurationEntity> {

    List<ModelConfigurationEntity> selectVisiblePage(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("limit") long limit,
            @Param("offset") long offset);

    long countVisible(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);
}
