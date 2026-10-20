package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 幂等定位使用普通 CRUD；调用方必须与声明和分类审计处于同一事务。 */
public interface PlatformModelDeploymentRequestMapper extends BaseMapper<PlatformModelDeploymentRequestEntity> { }
